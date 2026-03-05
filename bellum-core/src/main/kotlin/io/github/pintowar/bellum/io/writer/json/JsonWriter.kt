package io.github.pintowar.bellum.io.writer.json

import io.github.pintowar.bellum.core.io.ContentWriter
import io.github.pintowar.bellum.core.io.JsonEmployeeDto
import io.github.pintowar.bellum.core.io.JsonProjectDto
import io.github.pintowar.bellum.core.io.JsonTaskDto
import io.github.pintowar.bellum.core.io.ParsedProject
import kotlinx.serialization.json.Json

class JsonWriter : ContentWriter<ParsedProject> {
    companion object {
        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
    }

    override fun write(input: ParsedProject): String {
        val project = input.project
        val employees =
            project.allEmployees().mapIndexed { idx, emp ->
                val maxSkills =
                    emp.skills.keys.maxOfOrNull { it.removePrefix("skill").toInt() } ?: 0
                val skills =
                    (1..maxSkills).map { skillNum ->
                        emp.skills["skill$skillNum"]?.invoke() ?: 0
                    }
                JsonEmployeeDto(
                    id = idx + 1,
                    name = emp.name,
                    skills = skills,
                )
            }

        val tasks =
            project.allTasks().mapIndexed { idx, task ->
                val maxSkills =
                    task.requiredSkills.keys.maxOfOrNull { it.removePrefix("skill").toInt() } ?: 0
                val requiredSkills =
                    (1..maxSkills).map { skillNum ->
                        task.requiredSkills["skill$skillNum"]?.invoke() ?: 0
                    }
                val precedes =
                    task.dependsOn?.let { dep ->
                        project.allTasks().indexOfFirst { it.id() == dep.id() } + 1
                    } ?: -1
                JsonTaskDto(
                    id = idx + 1,
                    description = task.description,
                    priority = task.priority.name.lowercase(),
                    precedes = precedes,
                    requiredSkills = requiredSkills,
                )
            }

        val dto =
            JsonProjectDto(
                name = project.name,
                employees = employees,
                tasks = tasks,
                estimationMatrix = input.estimationMatrix,
            )

        return json.encodeToString(dto)
    }
}
