package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import java.time.Duration

data class SchedulerSolution(
    val project: Project,
    val optimal: Boolean,
    val duration: Duration
)