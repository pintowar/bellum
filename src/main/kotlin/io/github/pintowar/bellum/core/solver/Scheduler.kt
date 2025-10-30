package io.github.pintowar.bellum.core.solver

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class Scheduler {
    abstract val estimator: TimeEstimator

    private val isProcessing = AtomicBoolean(false)

    internal abstract fun innerSolve(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Result<SchedulerSolution>

    fun solve(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Result<SchedulerSolution> {
        if (!isProcessing.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("Scheduler is already processing."))
        }

        val result = innerSolve(project, timeLimit, callback)
        isProcessing.set(false)
        return result
    }

    fun allSolutions(
        project: Project,
        timeLimit: Duration = 1.minutes,
    ): Result<SolutionHistory> {
        val solutions = ConcurrentLinkedQueue<SchedulerSolution>()
        val finalSolution = solve(project, timeLimit) { solutions.add(it) }
        return finalSolution.map { SolutionHistory(solutions + it) }
    }
}
