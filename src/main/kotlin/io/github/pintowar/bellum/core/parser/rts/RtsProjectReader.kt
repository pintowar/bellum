package io.github.pintowar.bellum.core.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
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
            val lines = content.trim().lines()
            val idx = lines.indexOfFirst { it.startsWith("=====") }
            val (employeeContent, taskContent) = lines.take(idx) to lines.drop(idx + 1)

            val employees = RtsEmployeeReader.readContent(employeeContent.joinToString("\n")).getOrThrow()
            val tasks = RtsTaskReader.readContent(taskContent.joinToString("\n")).getOrThrow()

            return Project(name, Clock.System.now(), employees.toSet(), tasks.toSet())
        }
}
