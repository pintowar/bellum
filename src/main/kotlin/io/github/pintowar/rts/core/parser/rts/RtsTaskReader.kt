package io.github.pintowar.rts.core.parser.rts

import io.github.pintowar.rts.core.domain.SkillPoint
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.TaskPriority
import io.github.pintowar.rts.core.domain.UnassignedTask
import io.github.pintowar.rts.core.parser.ContentReader
import io.github.pintowar.rts.core.parser.InvalidFileFormat

object RtsTaskReader : ContentReader<List<Task>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<List<Task>> =
        runCatching {
            if (content.isBlank()) throw InvalidFileFormat("Empty task content.")
            val lines = content.lines()

            val (header, body) = lines.first().split(sep) to lines.drop(1).map { it.split(sep) }
            val data =
                body.mapIndexed { idx, line ->
                    if (header.size != line.size) {
                        throw InvalidFileFormat(
                            "Invalid task content on line ${idx + 1}: num contents (${line.size}) values does not match headers (${header.size}).",
                        )
                    }
                    val row = header.zip(line).toMap()
                    val (id, content, priority, precedes) = listOf("id", "content", "priority", "precedes").map(row::getValue)
                    val skills =
                        row
                            .filterKeys { it.startsWith("skill") }
                            .mapValues { SkillPoint(it.value.toInt()).getOrThrow() }

                    val task = UnassignedTask(content, TaskPriority.valueOf(priority.uppercase()), skills).getOrThrow()
                    (id to precedes) to task
                }
            val (table, tasks) = data.unzip()
            val (ids, precedes) = table.unzip()
            adjustDependencies(ids, precedes, tasks)
        }

    fun adjustDependencies(
        ids: List<String>,
        precedes: List<String>,
        tasks: List<Task>,
    ): List<Task> {
        val taskByKey = tasks.withIndex().associate { (idx, task) -> ids[idx] to task }
        val tasksWithDepsById =
            precedes
                .withIndex()
                .filter { (_, id) -> id != "-1" }
                .map { (idx, id) ->
                    val precedenceId = taskByKey[id] ?: throw InvalidFileFormat("Precedence ($id) of task (${ids[idx]}) not found.")
                    taskByKey.getValue(ids[idx]).changeDependency(precedenceId)
                }.associateBy { it.id }

        return tasks.map { tasksWithDepsById.getOrDefault(it.id, it) }
    }
}
