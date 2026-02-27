package io.github.pintowar.bellum.parser

import io.github.pintowar.bellum.core.domain.Project

/**
 * Represents a parsed project with its domain model and optional estimation matrix.
 *
 * @property project The parsed [Project] domain object containing employees and tasks
 * @property estimationMatrix Optional matrix of estimation values (employee x task)
 */
data class ParsedProject(
    val project: Project,
    val estimationMatrix: List<List<Long>>? = null,
)
