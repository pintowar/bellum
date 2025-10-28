package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface Scheduler {
    val estimator: TimeEstimator

    fun solve(
        project: Project,
        timeLimit: Duration,
    ): Result<SchedulerSolution> = solve(project, 1.minutes, Clock.System.now())

    fun solve(
        project: Project,
        timeLimit: Duration,
        startTime: Instant,
    ): Result<SchedulerSolution>
}
