package io.github.pintowar.bellum.core.parser.rts

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.context.bind
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat

object RtsEmployeeReader : ContentReader<List<Employee>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Either<Throwable, List<Employee>> =
        either {
            ensure(content.isNotBlank()) { InvalidFileFormat("Empty employee content.") }
            val lines = content.trim().lines()

            val headerLine = lines.firstOrNull()
            ensureNotNull(headerLine) { InvalidFileFormat("No header line found in employee content.") }

            val header = headerLine.split(sep)
            val body = lines.drop(1).map { it.split(sep) }

            body.mapIndexed { idx, line ->
                ensure(header.size == line.size) {
                    InvalidFileFormat(
                        "Invalid employee content on line ${idx + 1}: num contents (${line.size}) values does not match headers (${header.size}).",
                    )
                }

                val row = header.zip(line).toMap()
                val empContent = ensureNotNull(row["content"]) { InvalidFileFormat("Missing 'content' field in employee row.") }

                val skills =
                    row
                        .filterKeys { it.startsWith("skill") }
                        .mapValues { entry ->
                            Either
                                .catch { entry.value.toInt() }
                                .mapLeft { e -> InvalidFileFormat("Invalid skill value '${entry.value}': ${e.message}") }
                                .flatMap { SkillPoint(it) }
                                .bind()
                        }

                Employee(empContent, skills).bind()
            }
        }
}
