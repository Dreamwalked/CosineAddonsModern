package com.dreamwalked.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

object AutoUpdater {
    private const val MOD_ID = "cosine-addons-modern"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Dreamwalked/CosineAddonsModern/releases/latest"
    private const val MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024

    private val logger = LoggerFactory.getLogger("cosine-addons-modern-updater")
    private val checking = AtomicBoolean(false)
    private val messages = ConcurrentLinkedQueue<String>()
    private val toastId = SystemToast.SystemToastId(7_500L)
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            var message = messages.poll() ?: return@register
            while (true) {
                message = messages.poll() ?: break
            }
            val plainMessage = message.replace(Regex("§."), "")
            runCatching {
                SystemToast.addOrUpdate(
                    client.gui.toastManager(),
                    toastId,
                    Component.literal("Cosine Addons Updater"),
                    Component.literal(plainMessage)
                )
            }.onFailure {
                client.player?.sendSystemMessage(Component.literal(ChatUtils.PREFIX + message))
            }
        }

        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
            logger.info("Skipping automatic update check in a development environment.")
            return
        }
        checkForUpdates()
    }

    private fun checkForUpdates() {
        if (!checking.compareAndSet(false, true)) return
        Thread.ofVirtual().name("cosine-auto-updater").start {
            try {
                val mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                val currentJar = mod.origin.paths.singleOrNull {
                    Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true)
                } ?: run {
                    logger.warn("Cannot update because the running mod jar could not be identified.")
                    return@start
                }

                val currentVersion = mod.metadata.version
                announceCompletedUpdate(currentJar, currentVersion.friendlyString)

                recoverPendingUpdate(currentJar)?.let { pendingVersion ->
                    notifyUser("§aUpdate $pendingVersion has been installed. Restart the game to apply.")
                    return@start
                }

                val release = fetchLatestRelease() ?: return@start
                val remoteVersion = runCatching { Version.parse(release.version) }.getOrElse {
                    logger.warn("Ignoring release with invalid version tag: {}", release.tag)
                    return@start
                }
                if (remoteVersion <= currentVersion) return@start

                notifyUser("§eDownloading Cosine Addons ${release.version} update…")
                val staged = downloadAndValidate(release, currentJar.parent)
                scheduleInstall(currentJar, staged.pending, staged.target)
                notifyUser("§aCosine Addons ${release.version} downloaded. Restart the game to apply.")
            } catch (error: Exception) {
                logger.warn("Automatic update failed", error)
                notifyUser("§cAutomatic update failed; the current version will continue to work.")
            } finally {
                checking.set(false)
            }
        }
    }

    private fun fetchLatestRelease(): Release? {
        val request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "CosineAddonsModern-Updater")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) {
            logger.info("No published GitHub release is available yet.")
            return null
        }
        check(response.statusCode() == 200) { "GitHub release check returned HTTP ${response.statusCode()}" }

        val json = JsonParser.parseString(response.body()).asJsonObject
        val tag = json.get("tag_name")?.asString ?: error("Release has no tag")
        val version = tag.removePrefix("v").removePrefix("V")
        val asset = json.getAsJsonArray("assets")
            ?.map { it.asJsonObject }
            ?.firstOrNull(::isModJarAsset)
            ?: error("Release $tag has no installable mod jar asset")
        return Release(
            tag = tag,
            version = version,
            assetName = asset.get("name").asString,
            downloadUrl = asset.get("browser_download_url").asString,
            size = asset.get("size")?.asLong ?: -1L,
            digest = asset.get("digest")?.takeUnless { it.isJsonNull }?.asString
        )
    }

    private fun isModJarAsset(asset: JsonObject): Boolean {
        val name = asset.get("name")?.asString?.lowercase() ?: return false
        return name.endsWith(".jar") &&
            "cosine-addons-modern" in name &&
            listOf("sources", "javadoc", "dev", "shadow").none(name::contains)
    }

    private fun downloadAndValidate(release: Release, modsDirectory: Path): StagedUpdate {
        require(release.size in 1..MAX_DOWNLOAD_BYTES) { "Release asset has an invalid size" }
        val assetFileName = Path.of(release.assetName).fileName.toString()
        require(assetFileName == release.assetName) { "Release asset has an unsafe filename" }
        val target = modsDirectory.resolve(assetFileName)
        val part = modsDirectory.resolve(".$assetFileName.part")
        val pending = modsDirectory.resolve("$assetFileName.update")
        Files.deleteIfExists(part)

        val request = HttpRequest.newBuilder(URI.create(release.downloadUrl))
            .timeout(Duration.ofMinutes(2))
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "CosineAddonsModern-Updater")
            .GET()
            .build()
        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofFile(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        )
        check(response.statusCode() in 200..299) { "Update download returned HTTP ${response.statusCode()}" }
        check(Files.size(part) == release.size) { "Downloaded update size does not match GitHub metadata" }
        verifyDigest(part, release.digest)
        verifyModJar(part, release.version)
        Files.move(part, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return StagedUpdate(pending, target)
    }

    private fun verifyDigest(file: Path, expected: String?) {
        if (expected == null || !expected.startsWith("sha256:", ignoreCase = true)) return
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected.substringAfter(':'), ignoreCase = true)) {
            "Downloaded update failed its SHA-256 check"
        }
    }

    private fun verifyModJar(file: Path, expectedVersion: String): String {
        ZipFile(file.toFile()).use { zip ->
            val metadataEntry = zip.getEntry("fabric.mod.json") ?: error("Downloaded file is not a Fabric mod")
            val metadata = zip.getInputStream(metadataEntry).reader().use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
            check(metadata.get("id")?.asString == MOD_ID) { "Downloaded jar has the wrong mod id" }
            val version = metadata.get("version")?.asString ?: error("Downloaded jar has no version")
            check(version == expectedVersion) { "Downloaded jar version $version does not match $expectedVersion" }
            return version
        }
    }

    private fun recoverPendingUpdate(currentJar: Path): String? {
        Files.newDirectoryStream(currentJar.parent, "*cosine-addons-modern-*.jar.update").use { stream ->
            val pending = stream.firstOrNull() ?: return null
            val version = runCatching {
                readModVersion(pending)
            }.getOrElse {
                logger.warn("Removing an invalid staged update", it)
                Files.deleteIfExists(pending)
                return null
            }
            val targetName = pending.fileName.toString().removePrefix(".").removeSuffix(".update")
            val target = currentJar.parent.resolve(targetName)
            scheduleInstall(currentJar, pending, target)
            return version
        }
    }

    private fun announceCompletedUpdate(currentJar: Path, currentVersion: String) {
        val backup = currentJar.parent.resolve(".cosine-addons-modern-previous.jar.old")
        if (!Files.isRegularFile(backup)) return
        val oldVersion = runCatching { readModVersion(backup) }.getOrNull()
        if (oldVersion != null && oldVersion != currentVersion) {
            notifyUser("§aCosine Addons updated successfully from $oldVersion to $currentVersion.")
        }
        runCatching { Files.deleteIfExists(backup) }
            .onFailure { logger.warn("Could not remove the previous mod jar backup", it) }
    }

    private fun readModVersion(file: Path): String = ZipFile(file.toFile()).use { zip ->
        val metadataEntry = zip.getEntry("fabric.mod.json") ?: error("File is not a Fabric mod")
        val metadata = zip.getInputStream(metadataEntry).reader().use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        check(metadata.get("id")?.asString == MOD_ID) { "Jar has the wrong mod id" }
        metadata.get("version")?.asString ?: error("Jar has no version")
    }

    private fun scheduleInstall(currentJar: Path, pending: Path, target: Path) {
        val pid = ProcessHandle.current().pid()
        val backup = currentJar.parent.resolve(".cosine-addons-modern-previous.jar.old")
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val quote: (Path) -> String = { "'${it.toAbsolutePath().toString().replace("'", "''")}'" }
            val script = """
                Wait-Process -Id $pid -ErrorAction SilentlyContinue
                ${'$'}old = ${quote(currentJar)}
                ${'$'}new = ${quote(pending)}
                ${'$'}target = ${quote(target)}
                ${'$'}backup = ${quote(backup)}
                if (Test-Path -LiteralPath ${'$'}backup) { Remove-Item -LiteralPath ${'$'}backup -Force }
                for (${ '$' }attempt = 0; ${ '$' }attempt -lt 20; ${ '$' }attempt++) {
                    try {
                        if ((Test-Path -LiteralPath ${'$'}old) -and !(Test-Path -LiteralPath ${'$'}backup)) {
                            Move-Item -LiteralPath ${'$'}old -Destination ${'$'}backup -Force
                        }
                        Move-Item -LiteralPath ${'$'}new -Destination ${'$'}target -Force
                        exit 0
                    } catch {
                        Start-Sleep -Milliseconds 500
                    }
                }
                if (!(Test-Path -LiteralPath ${'$'}old) -and (Test-Path -LiteralPath ${'$'}backup)) {
                    Move-Item -LiteralPath ${'$'}backup -Destination ${'$'}old -Force
                }
                exit 1
            """.trimIndent()
            ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script
            ).start()
        } else {
            val shellQuote: (Path) -> String = { "'${it.toAbsolutePath().toString().replace("'", "'\\''")}'" }
            val old = shellQuote(currentJar)
            val staged = shellQuote(pending)
            val updateTarget = shellQuote(target)
            val oldBackup = shellQuote(backup)
            val script = "while kill -0 $pid 2>/dev/null; do sleep 1; done; " +
                "rm -f $oldBackup; " +
                "if mv -f $old $oldBackup; then mv -f $staged $updateTarget || mv -f $oldBackup $old; " +
                "else mv -f $staged $updateTarget; fi"
            ProcessBuilder("sh", "-c", script).start()
        }
    }

    private fun notifyUser(message: String) {
        messages += message
    }

    private data class Release(
        val tag: String,
        val version: String,
        val assetName: String,
        val downloadUrl: String,
        val size: Long,
        val digest: String?
    )

    private data class StagedUpdate(
        val pending: Path,
        val target: Path
    )
}
