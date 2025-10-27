package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import kotlin.time.Duration

data class SchedulerSolution(
    val project: Project,
    val optimal: Boolean,
    val duration: Duration,
)
