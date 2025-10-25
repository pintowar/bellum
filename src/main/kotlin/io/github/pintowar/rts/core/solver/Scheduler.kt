package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import java.time.Instant

interface Scheduler {
    val estimator: TimeEstimator

    fun solve(project: Project): Result<SchedulerSolution> = solve(project, Instant.now())

    fun solve(
        project: Project,
        start: Instant,
    ): Result<SchedulerSolution>
}
