package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class Scheduler {
    abstract val estimator: TimeEstimator
    protected val listeners: MutableList<(solution: SchedulerSolution) -> Unit> = mutableListOf()

    abstract fun solve(
        project: Project,
        timeLimit: Duration = 1.minutes,
    ): Result<SchedulerSolution>

    fun addSolutionListener(listener: (solution: SchedulerSolution) -> Unit) = listeners.add(listener)
}
