package io.github.pintowar.bellum.core.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import kotlinx.datetime.Clock
import java.net.URI

class RtsProjectReader(
    private val name: String,
) : ContentReader<Project> {
    companion object {
        private fun content(uri: String) = URI(uri).toURL().readText()

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
