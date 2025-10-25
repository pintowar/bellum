package io.github.pintowar.rts.core.parser.rts

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.parser.ContentReader

object RtsProjectReader : ContentReader<Project> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<Project> =
        runCatching {
            val lines = content.lines()
            val idx = lines.indexOfFirst { it.startsWith("=====") }
            val (employeeContent, taskContent) = lines.take(idx) to lines.drop(idx + 1)

            val employees = RtsEmployeeReader.readContent(employeeContent.joinToString("\n")).getOrThrow()
            val tasks = RtsTaskReader.readContent(taskContent.joinToString("\n")).getOrThrow()

            Project(employees.toSet(), tasks.toSet()).getOrThrow()
        }
}
