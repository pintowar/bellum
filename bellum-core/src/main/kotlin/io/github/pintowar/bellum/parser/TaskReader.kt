package io.github.pintowar.bellum.parser

import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.parser.InvalidFileFormat

object TaskReader {
    /**
     * Adjusts task dependencies based on task IDs and precedence relationships.
     *
     * @param ids List of task IDs in order
     * @param precedes List of precedence IDs (task IDs that must come before each task)
     * @param tasks List of tasks to adjust
     * @return List of tasks with dependencies applied
     */
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
                    val precedenceId =
                        taskByKey[id] ?: throw InvalidFileFormat("Precedence ($id) of task (${ids[idx]}) not found.")
                    taskByKey.getValue(ids[idx]).changeDependency(precedenceId)
                }.associateBy { it.id }

        return tasks.map { tasksWithDepsById.getOrDefault(it.id, it) }
    }
}
