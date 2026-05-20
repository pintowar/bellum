import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.the

/**
 * Provides access to the `libs` version catalog.
 *
 * @return The `LibrariesForLibs` accessor.
 */
val Project.libs: LibrariesForLibs
    get() = the<LibrariesForLibs>()

/**
 * Resolves the target operating system name for native compilation.
 *
 * @return "linux", "darwin", or "win32" depending on the host OS.
 */
val Project.osName: String
    get() = when (val os = OperatingSystem.current()) {
        OperatingSystem.LINUX -> "linux"
        OperatingSystem.MAC_OS -> "darwin"
        OperatingSystem.WINDOWS -> "win32"
        else -> error("Unsupported OS: ${os.name}")
    }

/**
 * Resolves the target CPU architecture for native compilation.
 *
 * @return "x86-64" or "aarch64" depending on the host architecture.
 */
val Project.osArch: String
    get() = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x86-64"
        "aarch64" -> "aarch64"
        else -> error("Unsupported architecture: ${System.getProperty("os.arch")}")
    }