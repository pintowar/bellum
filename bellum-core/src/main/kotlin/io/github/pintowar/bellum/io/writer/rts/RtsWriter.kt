package io.github.pintowar.bellum.io.writer.rts

import io.github.pintowar.bellum.core.io.ContentWriter
import io.github.pintowar.bellum.core.io.ParsedProject

class RtsWriter : ContentWriter<ParsedProject> {
    companion object {
        private const val SEP = ","
    }

    override fun write(
        input: ParsedProject,
        omitSkills: Boolean,
    ): String {
        val project = input.project
        val employees = project.allEmployees()
        val tasks = project.allTasks()

        val maxSkillCount =
            maxOf(
                employees.flatMap { it.skills.keys }.maxOfOrNull { it.removePrefix("skill").toInt() } ?: 0,
                tasks.flatMap { it.requiredSkills.keys }.maxOfOrNull { it.removePrefix("skill").toInt() } ?: 0,
            )

        val employeeHeader = listOf("id", "content") + (1..maxSkillCount).map { "skill$it" }
        val employeeLines =
            employees.withIndex().map { (idx, emp) ->
                val skills =
                    (1..maxSkillCount).map { skillNum ->
                        emp.skills["skill$skillNum"]?.invoke()?.toString() ?: "0"
                    }
                listOf((idx + 1).toString(), emp.name) + skills
            }
        val employeeSection = (listOf(employeeHeader) + employeeLines).joinToString("\n") { it.joinToString(SEP) }

        val taskHeader = listOf("id", "content", "priority", "precedes") + (1..maxSkillCount).map { "skill$it" }
        val taskLines =
            tasks.withIndex().map { (idx, task) ->
                val skills =
                    (1..maxSkillCount).map { skillNum ->
                        task.requiredSkills["skill$skillNum"]?.invoke()?.toString() ?: "0"
                    }
                val precedes =
                    task.dependsOn?.let { dep ->
                        tasks.indexOfFirst { it.id() == dep.id() } + 1
                    } ?: -1
                listOf(
                    (idx + 1).toString(),
                    task.description,
                    task.priority.name.lowercase(),
                    precedes.toString(),
                ) + skills
            }
        val taskSection = (listOf(taskHeader) + taskLines).joinToString("\n") { it.joinToString(SEP) }

        val matrixSection =
            input.estimationMatrix?.let { matrix ->
                matrix.joinToString("\n") { row -> row.joinToString(SEP) }
            } ?: ""

        return buildString {
            append(employeeSection)
            append("\n=================\n")
            append(taskSection)
            if (matrixSection.isNotBlank()) {
                append("\n=================\n")
                append(matrixSection)
            }
        }
    }
}
