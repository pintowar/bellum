package io.github.pintowar.bellum.core.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import kotlinx.datetime.Clock

class RtsProjectReader(
    private val name: String,
) : ContentReader<Project> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<Project> =
        runCatching {
            val lines = content.trim().lines()
            val idx = lines.indexOfFirst { it.startsWith("=====") }
            val (employeeContent, taskContent) = lines.take(idx) to lines.drop(idx + 1)

            val employees = RtsEmployeeReader.readContent(employeeContent.joinToString("\n")).getOrThrow()
            val tasks = RtsTaskReader.readContent(taskContent.joinToString("\n")).getOrThrow()

            return Project(name, Clock.System.now(), employees.toSet(), tasks.toSet())
        }
}
