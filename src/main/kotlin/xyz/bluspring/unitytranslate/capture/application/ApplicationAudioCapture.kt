package xyz.bluspring.unitytranslate.capture.application

import org.jetbrains.annotations.ApiStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.bluspring.unitytranslate.capture.application.source.SourceInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.io.path.*

object ApplicationAudioCapture {
    fun createCapture(sampleRate: Int, channels: Int, onCaptureAudio: AudioCaptureFrameEvent): AudioCaptureData {
        return AudioCaptureData(sampleRate, channels, onCaptureAudio)
    }

    fun listAllSources(): List<SourceInfo> {
        return this.listSources().toList()
    }

    @ApiStatus.Internal
    external fun startCaptureId(sampleRate: Int, channels: Byte, processId: Int, callback: AudioCaptureFrameEvent): Long
    @ApiStatus.Internal
    external fun startCaptureName(sampleRate: Int, channels: Byte, processName: String, callback: AudioCaptureFrameEvent): Long
    @ApiStatus.Internal
    external fun startCaptureSystemAudio(sampleRate: Int, channels: Byte, callback: AudioCaptureFrameEvent): Long
    @ApiStatus.Internal
    external fun stopCapture(sessionPtr: Long)

    private external fun listSources(): Array<SourceInfo>

    private val logger: Logger = LoggerFactory.getLogger(ApplicationAudioCapture::class.java)

    private var hasTriedLoading = false
    private var isLoaded = false

    fun autoLoad() {
        try {
            this.autoLoadOrThrow()
        } catch (e: Throwable) {
            logger.error("Failed to load ApplicationAudioCapture!", e)
            logger.warn("ApplicationAudioCapture may not be supported on platform ${System.getProperty("os.name")} (${System.getProperty("os.arch")})!")
            logger.warn("As a result, ApplicationAudioCapture will not be active, and may cause errors if any native calls are attempted.")
        } finally {
            this.hasTriedLoading = true
        }
    }

    // Modified from ImGui-java's library loading - https://github.com/SpaiR/imgui-java/blob/main/imgui-binding/src/main/java/imgui/ImGui.java
    fun autoLoadOrThrow() {
        if (this.isLoaded)
            return

        val libPath = System.getProperty("application-audio-capture.library.path")
        val fullLibName = System.getProperty("application-audio-capture.library.name", System.mapLibraryName("ApplicationAudioCapture"))

        if (libPath != null) {
            System.load(Path(libPath).resolve(fullLibName).absolutePathString())
        } else {
            try {
                System.loadLibrary(fullLibName)
            } catch (e: Throwable) {
                val extractedPath = try {
                    tryLoadFromClassPath(fullLibName)
                } catch (e2: Exception) {
                    val joined = RuntimeException("Failed to load natives for ApplicationAudioCapture!")
                    joined.addSuppressed(e2)
                    joined.addSuppressed(e)

                    throw joined
                }

                System.load(extractedPath.resolve(fullLibName).absolutePathString())
            }
        }

        this.isLoaded = true
    }

    private fun tryLoadFromClassPath(fullLibName: String): Path {
        val classLoader = ApplicationAudioCapture::class.java.classLoader

        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        val osArch = System.getProperty("os.arch").lowercase().run {
            if (isWindows && this == "amd64")
                "x64"
            else this
        }

        val dir = "ApplicationAudioCapture/${if (isWindows) "windows" else if (isMac) "osx" else "linux"}/${osArch}"

        val libraryResource = classLoader.getResource("$dir/$fullLibName")
            ?: throw Exception("Could not locate ApplicationAudioCapture natives for platform $osName $osArch!")

        val tmpDir = Path(System.getProperty("java.io.tmpdir")).resolve("application-audio-capture-natives")

        if (!tmpDir.exists())
            tmpDir.createDirectories()

        // first, try to hash the file
        val digest = MessageDigest.getInstance("MD5")
        libraryResource.openStream().use {
            DigestInputStream(it, digest).readAllBytes()
        }

        val expectedHash = digest.digest().toHexString()
        val hashedTmpDir = tmpDir.resolve(expectedHash)
        if (!hashedTmpDir.exists())
            hashedTmpDir.createDirectories()

        val libraryPath = hashedTmpDir.resolve(fullLibName)
        val lockFile = hashedTmpDir.resolve("session.lock")

        if (libraryPath.exists()) {
            // hash the file and see if it matches
            libraryPath.inputStream(StandardOpenOption.READ).use {
                DigestInputStream(it, digest).readAllBytes()
            }

            // hash matches, we're good
            if (expectedHash == digest.digest().toHexString())
                return hashedTmpDir

            // nope, let's see if someone's currently copying it.
            // if so, we should block the thread until the lock is invalid.
            if (lockFile.exists()) {
                val pid = lockFile.readText().trim().toLongOrNull()
                if (pid != null && ProcessHandle.of(pid).isPresent) {
                    while (true) {
                        // check if the lock file still exists
                        val pid = if (lockFile.exists()) {
                            lockFile.readText().trim().toLongOrNull()
                        } else break

                        // also check if the process still exists
                        if (pid == null || ProcessHandle.of(pid).isEmpty)
                            break

                        // let's not check too frequently...
                        Thread.sleep(2_500L)
                    }

                    // okay, let's check again just to be safe.
                    libraryPath.inputStream(StandardOpenOption.READ).use {
                        DigestInputStream(it, digest).readAllBytes()
                    }

                    if (expectedHash == digest.digest().toHexString())
                        return hashedTmpDir
                }

                // fuck, okay let's continue extracting us I guess, we assume the program crashed or failed or something.
            }
        }

        libraryResource.openStream().use {
            try {
                // we want to make sure we're not copying all at once.
                lockFile.writeText("${ProcessHandle.current().pid()}", options = arrayOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))

                Files.copy(it, libraryPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AccessDeniedException) {
                if (!libraryPath.exists())
                    throw e
            } finally {
                // okay, we're done here.
                lockFile.deleteIfExists()
            }
        }

        if (!libraryPath.exists())
            throw Exception("Failed to extract library files for ApplicationAudioCapture!")
        else {
            libraryPath.inputStream(StandardOpenOption.READ).use {
                DigestInputStream(it, digest).readAllBytes()
            }

            // uh oh
            val actualHash = digest.digest().toHexString()
            if (expectedHash != actualHash)
                throw IllegalStateException("Extracted library hash for ApplicationAudioCapture does not match! (expected: $expectedHash, got: $actualHash)")
        }

        return hashedTmpDir
    }

    @JvmStatic
    fun isAvailable(): Boolean {
        if (!this.hasTriedLoading) {
            this.autoLoad()
        }

        return this.isLoaded
    }
}
