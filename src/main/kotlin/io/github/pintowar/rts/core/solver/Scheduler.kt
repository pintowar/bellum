package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface Scheduler {
    val estimator: TimeEstimator

    fun solve(project: Project): Result<SchedulerSolution> = solve(project, Clock.System.now())

    fun solve(
        project: Project,
        start: Instant,
    ): Result<SchedulerSolution>
}
