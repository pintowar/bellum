package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class Scheduler {
    abstract val estimator: TimeEstimator

    abstract fun solve(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Result<SchedulerSolution>

    fun allSolutions(
        project: Project,
        timeLimit: Duration = 1.minutes,
    ): Result<SolutionHistory> {
        val solutions = mutableListOf<SchedulerSolution>()
        val finalSolution = solve(project, timeLimit) { solutions.add(it) }
        return finalSolution.map { SolutionHistory(solutions + it) }
    }
}
