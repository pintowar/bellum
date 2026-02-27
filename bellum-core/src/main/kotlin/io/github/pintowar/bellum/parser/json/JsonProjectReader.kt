package io.github.pintowar.bellum.parser.json

import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.github.pintowar.bellum.parser.ParsedProject
import io.github.pintowar.bellum.parser.TaskReader.adjustDependencies
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Parser for JSON-formatted project files.
 *
 * Supports reading projects from JSON format with the following structure:
 * - `name`: Optional project name
 * - `employees`: List of employees with skills
 * - `tasks`: List of tasks with required skills and priorities
 * - `estimationMatrix`: Optional 2D matrix of estimation values
 *
 * @property defaultName Default name to use when project name is not specified in JSON
 */
class JsonProjectReader(
    private val defaultName: String = "Unnamed Project",
) : ContentReader<ParsedProject> {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Convenience method to parse JSON content directly.
         *
         * @param content The raw JSON content string
         * @param defaultName The default project name
         * @return Result containing the parsed ParsedProject or an error
         */
        fun readContent(
            content: String,
            defaultName: String = "Unnamed Project",
        ): Result<ParsedProject> = JsonProjectReader(defaultName).readContent(content)
    }

    override fun readContent(content: String): Result<ParsedProject> =
        runCatching {
            val trimmedContent = content.trim()
            if (trimmedContent.isBlank()) {
                throw InvalidFileFormat("Empty project content.")
            }
            val parsed = json.decodeFromString<JsonProjectDto>(content)
            val project =
                Project(
                    name = parsed.name ?: defaultName,
                    kickOff = Clock.System.now(),
                    employees = convertEmployees(parsed.employees).toSet(),
                    tasks = convertTasks(parsed.tasks).toSet(),
                ).getOrThrow()

            val matrix =
                validateMatrix(
                    parsed.estimationMatrix,
                    project.allEmployees().size,
                    project.allTasks().size,
                ).getOrThrow()
            ParsedProject(project, matrix)
        }

    /**
     * Converts JSON employee DTOs to domain Employee objects.
     *
     * @param parsed List of JSON employee DTOs
     * @return List of Employee domain objects
     */
    fun convertEmployees(parsed: List<JsonEmployeeDto>): List<Employee> =
        parsed.map {
            val skills =
                it.skills.withIndex().associate { (i, el) -> "skill${i + 1}" to SkillPoint(el).getOrThrow() }
            Employee(it.name, skills).getOrThrow()
        }

    /**
     * Converts JSON task DTOs to domain Task objects.
     *
     * @param parsed List of JSON task DTOs
     * @return List of Task domain objects
     */
    fun convertTasks(parsed: List<JsonTaskDto>): List<Task> {
        val data =
            parsed.map {
                val skills =
                    it.requiredSkills
                        .withIndex()
                        .associate { (i, el) -> "skill${i + 1}" to SkillPoint(el).getOrThrow() }
                val priority = TaskPriority.valueOf(it.priority.uppercase())
                val task = UnassignedTask.invoke(it.description, priority, skills).getOrThrow()

                (it.id to it.precedes) to task
            }

        val (table, tasks) = data.unzip()
        val (ids, precedes) = table.unzip()
        return adjustDependencies(ids.map { it.toString() }, precedes.map { it.toString() }, tasks)
    }

    /**
     * Validates that the estimation matrix has correct dimensions.
     *
     * @param matrix The estimation matrix to validate
     * @param employeesSize Expected number of rows (employees)
     * @param tasksSize Expected number of columns (tasks)
     * @return Result containing the validated matrix or null if not provided
     */
    fun validateMatrix(
        matrix: List<List<Long>>?,
        employeesSize: Int,
        tasksSize: Int,
    ): Result<List<List<Long>>?> {
        if (matrix == null) return Result.success(null)
        if (matrix.size != employeesSize) {
            return Result.failure(
                InvalidFileFormat(
                    "Matrix has ${matrix.size} employee rows but project has $employeesSize employees.",
                ),
            )
        }
        matrix.firstOrNull()?.let { row ->
            if (row.size != tasksSize) {
                return Result.failure(
                    InvalidFileFormat(
                        "Matrix row has ${row.size} values but project has $tasksSize tasks.",
                    ),
                )
            }
        }
        return Result.success(matrix)
    }
}
