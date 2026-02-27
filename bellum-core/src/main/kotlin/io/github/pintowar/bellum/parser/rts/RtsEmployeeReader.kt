package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat

/**
 * Parser for employee data in RTS (Resource Task Scheduling) format.
 *
 * Expects CSV-like content where:
 * - First line is the header with column names
 * - Remaining lines are employee data
 * - Columns starting with "skill" are treated as skill levels
 *
 * Example:
 * ```
 * id,name,skill1,skill2
 * 1,Alice,5,3
 * 2,Bob,2,4
 * ```
 */
object RtsEmployeeReader : ContentReader<List<Employee>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<List<Employee>> =
        runCatching {
            if (content.isBlank()) throw InvalidFileFormat("Empty employee content.")
            val lines = content.trim().lines()

            val (header, body) = lines.first().split(sep) to lines.drop(1).map { it.split(sep) }
            body.mapIndexed { idx, line ->
                if (header.size != line.size) {
                    throw InvalidFileFormat(
                        "Invalid employee content on line ${idx + 1}: num contents (${line.size}) values does not match headers (${header.size}).",
                    )
                }
                val row = header.zip(line).toMap()
                val content = row.getValue("content")
                val skills =
                    row
                        .filterKeys { it.startsWith("skill") }
                        .mapValues { SkillPoint(it.value.toInt()).getOrThrow() }

                Employee(content, skills).getOrThrow()
            }
        }
}
