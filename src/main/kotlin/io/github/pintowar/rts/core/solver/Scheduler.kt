package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class Scheduler {
    abstract val estimator: TimeEstimator
    protected val listeners: MutableList<(solution: SchedulerSolution) -> Unit> = mutableListOf()

    fun solve(
        project: Project,
        timeLimit: Duration = 1.minutes,
    ): Result<SchedulerSolution> = solve(project, timeLimit, Clock.System.now())

    abstract fun solve(
        project: Project,
        timeLimit: Duration,
        startTime: Instant,
    ): Result<SchedulerSolution>

    fun addSolutionListener(listener: (solution: SchedulerSolution) -> Unit) = listeners.add(listener)
}
