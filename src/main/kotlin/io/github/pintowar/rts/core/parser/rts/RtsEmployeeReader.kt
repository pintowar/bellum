package io.github.pintowar.rts.core.parser.rts

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.SkillPoint
import io.github.pintowar.rts.core.parser.ContentReader

object RtsEmployeeReader : ContentReader<List<Employee>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<List<Employee>> =
        runCatching {
            val lines = content.lines()
            val (header, body) = lines.first().split(sep) to lines.drop(1).map { it.split(sep) }
            body.map { line ->
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
