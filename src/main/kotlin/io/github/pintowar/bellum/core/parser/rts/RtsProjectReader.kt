package io.github.pintowar.bellum.core.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import java.net.URI
import kotlin.time.Clock

class RtsProjectReader(
    private val name: String,
) : ContentReader<Project> {
    companion object {
        private fun content(uri: String) = URI(uri).toURL().readText()

        /**
         * Reads project content from a file path, trying multiple URI formats.
         * First tries the URI as-is, then file://base/uri, then file://uri.
         * @param base The base directory path
         * @param uri The file path relative to base or absolute
         * @return Result containing the parsed Project or an error
         */
        fun readContentFromPath(
            base: String,
            uri: String,
        ): Result<Project> =
            Result
                .success(uri)
                .mapCatching { content(it) }
                .recoverCatching { content("file://$base/$uri") }
                .recoverCatching { content("file://$uri") }
                .mapCatching {
                    RtsProjectReader("Sample input").readContent(it).getOrThrow()
                }
    }

    /**
     * Parses the project content string into a Project domain object.
     * Expects a format with employees above a separator line and tasks below.
     * @param content The raw content string to parse
     * @param sep The delimiter used within employee and task sections
     * @return Result containing the parsed Project or an error
     */
    override fun readContent(
        content: String,
        sep: String,
    ): Result<Project> =
        runCatching {
            val trimmedContent = content.trim()
            if (trimmedContent.isBlank()) {
                throw InvalidFileFormat("Empty project content.")
            }

            val lines = trimmedContent.lines()
            val idx =
                lines.indexOfFirst { line ->
                    line.isNotBlank() && line.all { it == '=' || it == '-' || it == '_' || it == ' ' }
                }

            if (idx == -1) {
                throw InvalidFileFormat("Missing separator line.")
            }

            val (employeeLines, taskLines) = lines.take(idx) to lines.drop(idx + 1)
            val employeeContent = employeeLines.joinToString("\n")
            val taskContent = taskLines.joinToString("\n")

            val employees =
                if (employeeContent.isBlank()) {
                    emptyList()
                } else {
                    RtsEmployeeReader.readContent(employeeContent).getOrThrow()
                }

            val tasks =
                if (taskContent.isBlank()) {
                    emptyList()
                } else {
                    RtsTaskReader.readContent(taskContent).getOrThrow()
                }

            return Project(name, Clock.System.now(), employees.toSet(), tasks.toSet())
        }
}
