import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

/**
 * Checks if the project version is a snapshot version.
 *
 * @return `true` if the project version ends with "SNAPSHOT", `false` otherwise.
 */
val Project.isSnapshotVersion: Boolean
    get() = version.toString().endsWith("SNAPSHOT")

/**
 * Provides access to the `libs` version catalog.
 *
 * @return The `LibrariesForLibs` accessor.
 */
val Project.libs: LibrariesForLibs
    get() = the<LibrariesForLibs>()