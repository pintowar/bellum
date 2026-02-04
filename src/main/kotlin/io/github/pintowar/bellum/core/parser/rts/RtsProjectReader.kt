package io.github.pintowar.bellum.core.parser.rts

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.recover
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
            either {
                val contentText =
                    recover({
                        Either.catch { content(uri) }.bind()
                    }) { _: Throwable ->
                        recover({
                            Either.catch { content("file://$base/$uri") }.bind()
                        }) { _: Throwable ->
                            recover({
                                Either.catch { content("file://$uri") }.bind()
                            }) { e: Throwable ->
                                raise(InvalidFileFormat("Could not read content from URI: ${e.message}"))
                            }
                        }
                    }

                ensure(contentText.isNotBlank()) { InvalidFileFormat("Empty project content.") }

                val reader = RtsProjectReader("Sample input")
                reader.readContent(contentText).bind()
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
