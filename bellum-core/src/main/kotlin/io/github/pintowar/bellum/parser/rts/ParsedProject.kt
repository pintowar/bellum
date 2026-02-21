package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.domain.Project

data class ParsedProject(
    val project: Project,
    val estimationMatrix: List<List<Long>>? = null,
)
