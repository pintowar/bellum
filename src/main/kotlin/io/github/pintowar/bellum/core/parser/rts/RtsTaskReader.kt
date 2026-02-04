package io.github.pintowar.bellum.core.parser.rts

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat

object RtsTaskReader : ContentReader<List<Task>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Either<Throwable, List<Task>> =
        either {
            ensure(content.isNotBlank()) { InvalidFileFormat("Empty task content.") }

            val lines = content.trim().lines()
            val headerLine = lines.firstOrNull()
            ensureNotNull(headerLine) { InvalidFileFormat("No header line found in task content.") }

            val header = headerLine.split(sep)
            val body = lines.drop(1).map { it.split(sep) }

            val data =
                body.mapIndexed { idx, line ->
                    ensure(header.size == line.size) {
                        InvalidFileFormat(
                            "Invalid task content on line ${idx + 1}: num contents (${line.size}) values does not match headers (${header.size}).",
                        )
                    }

                    val row = header.zip(line).toMap()
                    val id = ensureNotNull(row["id"]) { InvalidFileFormat("Missing 'id' field in task row.") }
                    val taskContent = ensureNotNull(row["content"]) { InvalidFileFormat("Missing 'content' field in task row.") }
                    val priority =
                        row["criticity"]
                            ?: row["priority"]
                            ?: raise(InvalidFileFormat("Missing 'priority' or 'criticity' field in task row."))
                    val precedes = ensureNotNull(row["precedes"]) { InvalidFileFormat("Missing 'precedes' field in task row.") }

                    val skills =
                        row
                            .filterKeys { it.startsWith("skill") }
                            .mapValues { entry ->
                                parseSkill(entry.key, entry.value).bind()
                            }

                    val taskPriority = parseTaskPriority(priority).bind()
                    val task = UnassignedTask(taskContent, taskPriority, skills).bind()
                    (id to precedes) to task
                }

            val (table, tasks) = data.unzip()
            val (ids, precedes) = table.unzip()
            adjustDependencies(ids, precedes, tasks).bind()
        }

    private fun parseSkill(
        key: String,
        value: String,
    ): Either<InvalidFileFormat, SkillPoint> =
        either {
            val points =
                Either
                    .catch { value.toInt() }
                    .mapLeft { e -> InvalidFileFormat("Invalid skill value '$value': ${e.message}") }
                    .bind()
            SkillPoint(points).mapLeft { e -> InvalidFileFormat("Invalid skill value '$value': ${e.message}") }.bind()
        }

    private fun parseTaskPriority(priority: String): Either<InvalidFileFormat, TaskPriority> =
        Either
            .catch { TaskPriority.valueOf(priority.uppercase()) }
            .mapLeft { InvalidFileFormat("Invalid priority value: $priority") }

    private fun adjustDependencies(
        ids: List<String>,
        precedes: List<String>,
        tasks: List<Task>,
    ): Either<Throwable, List<Task>> =
        either {
            val taskByKey = tasks.withIndex().associate { (idx, task) -> ids[idx] to task }
            val tasksWithDepsById =
                precedes
                    .withIndex()
                    .filter { (_, id) -> id != "-1" }
                    .associate { (idx, id) ->
                        val precedenceTask =
                            taskByKey[id]
                                ?: raise(InvalidFileFormat("Precedence ($id) of task (${ids[idx]}) not found."))
                        val taskWithDep = taskByKey.getValue(ids[idx]).changeDependency(precedenceTask)
                        taskWithDep.id to taskWithDep
                    }

            tasks.map { tasksWithDepsById.getOrDefault(it.id, it) }
        }
}
