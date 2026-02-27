package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.github.pintowar.bellum.parser.TaskReader.adjustDependencies

/**
 * Parser for task data in RTS (Resource Task Scheduling) format.
 *
 * Expects CSV-like content where:
 * - First line is the header with column names
 * - Remaining lines are task data
 * - Columns starting with "skill" are treated as required skill levels
 * - Supports both "priority" and "criticity" column names
 * - "precedes" column defines task dependencies (-1 means no dependency)
 *
 * Example:
 * ```
 * id,description,priority,precedes,skill1,skill2
 * 1,Task 1,minor,-1,3,2
 * 2,Task 2,major,1,5,0
 * ```
 *
 * @property sep The delimiter used to separate values in each line (default: ",")
 */
class RtsTaskReader(
    private val sep: String = ",",
) : ContentReader<List<Task>> {
    override fun readContent(content: String): Result<List<Task>> =
        runCatching {
            if (content.isBlank()) throw InvalidFileFormat("Empty task content.")
            val lines = content.trim().lines()

            val (header, body) = lines.first().split(sep) to lines.drop(1).map { it.split(sep) }
            val data =
                body.mapIndexed { idx, line ->
                    if (header.size != line.size) {
                        throw InvalidFileFormat(
                            "Invalid task content on line ${idx + 1}: num contents (${line.size}) values does not match headers (${header.size}).",
                        )
                    }
                    val row = header.zip(line).toMap()
                    val (id, content, priority, precedes) =
                        listOf(
                            row.getValue("id"),
                            row.getValue("content"),
                            row["criticity"] ?: row.getValue("priority"),
                            row.getValue("precedes"),
                        )
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
}
