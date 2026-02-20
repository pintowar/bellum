package io.github.pintowar.bellum.core.solver

import io.github.pintowar.bellum.core.domain.Project
import kotlin.time.Duration

data class SchedulerSolution(
    val project: Project,
    val optimal: Boolean,
    val duration: Duration,
    val stats: Map<String, Any> = emptyMap(),
)
