package io.github.pintowar.bellum.core.parser.rts

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import kotlinx.datetime.Clock
import java.net.URI

class RtsProjectReader(
    private val name: String,
) : ContentReader<Project> {
    companion object {
        private fun content(uri: String): String = URI(uri).toURL().readText()

        fun readContentFromPath(
            base: String,
            uri: String,
        ): Either<Throwable, Project> =
            Either.catch {
                val contentText =
                    try {
                        content(uri)
                    } catch (_: Exception) {
                        try {
                            content("file://$base/$uri")
                        } catch (_: Exception) {
                            try {
                                content("file://$uri")
                            } catch (e: Exception) {
                                throw InvalidFileFormat("Could not read content from URI: ${e.message}")
                            }
                        }
                    }
                if (contentText.isBlank()) {
                    throw InvalidFileFormat("Empty project content.")
                }
                RtsProjectReader("Sample input").readContent(contentText).getOrElse { throw it }
            }
    }

    override fun readContent(
        content: String,
        sep: String,
    ): Either<Throwable, Project> =
        either {
            val trimmedContent = content.trim()
            ensure(trimmedContent.isNotBlank()) { InvalidFileFormat("Empty project content.") }

            val lines = trimmedContent.lines()
            val idx =
                lines.indexOfFirst { line ->
                    line.isNotBlank() && line.all { it == '=' || it == '-' || it == '_' || it == ' ' }
                }

            ensure(idx != -1) { InvalidFileFormat("Missing separator line.") }

            val (employeeLines, taskLines) = lines.take(idx) to lines.drop(idx + 1)
            val employeeContent = employeeLines.joinToString("\n")
            val taskContent = taskLines.joinToString("\n")

            val employees =
                if (employeeContent.isBlank()) {
                    emptyList()
                } else {
                    RtsEmployeeReader.readContent(employeeContent).bind()
                }

            val tasks =
                if (taskContent.isBlank()) {
                    emptyList()
                } else {
                    RtsTaskReader.readContent(taskContent).bind()
                }

            Project(name, Clock.System.now(), employees.toSet(), tasks.toSet()).bind()
        }
}
