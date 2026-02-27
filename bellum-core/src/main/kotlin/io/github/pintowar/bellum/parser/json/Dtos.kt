package io.github.pintowar.bellum.parser.json

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for employee data in JSON format.
 *
 * @property id Unique employee identifier
 * @property name Employee name
 * @property skills List of skill levels for each skill
 */
@Serializable
data class JsonEmployeeDto(
    val id: Int,
    val name: String,
    val skills: List<Int> = emptyList(),
)

/**
 * Data Transfer Object for task data in JSON format.
 *
 * @property id Unique task identifier
 * @property description Task description
 * @property priority Task priority (minor, major, critical)
 * @property precedes ID of the task that must be completed before this task (-1 for no dependency)
 * @property requiredSkills List of required skill levels for each skill
 */
@Serializable
data class JsonTaskDto(
    val id: Int,
    val description: String,
    val priority: String,
    val precedes: Int,
    val requiredSkills: List<Int> = emptyList(),
)

/**
 * Data Transfer Object for project data in JSON format.
 *
 * @property name Optional project name
 * @property employees List of employees
 * @property tasks List of tasks
 * @property estimationMatrix Optional 2D matrix of estimation values (employees x tasks)
 */
@Serializable
data class JsonProjectDto(
    val name: String? = null,
    val employees: List<JsonEmployeeDto> = emptyList(),
    val tasks: List<JsonTaskDto> = emptyList(),
    val estimationMatrix: List<List<Long>>? = null,
)
