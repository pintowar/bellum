package io.github.pintowar.bellum.core.solver

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class Scheduler {
    abstract val estimator: TimeEstimator

    private val isProcessing = AtomicBoolean(false)

    /**
     * Core optimization algorithm implementation.
     *
     * This internal method contains the actual solving logic for the scheduling optimization problem.
     * It should implement a specific optimization algorithm (e.g., constraint programming,
     * linear programming, heuristic search) to find feasible schedules.
     *
     * @param project The project containing tasks and employees to schedule
     * @param timeLimit Maximum time allowed for optimization (default: 1 minute)
     * @param callback Optional callback invoked for each solution found during optimization
     * @return Result containing the optimal solution or failure with error details
     */
    internal abstract fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Either<Throwable, SchedulerSolution>

    /**
     * Finds the optimal schedule for a given project within the specified time limit.
     *
     * This method orchestrates the optimization process, manages thread safety, and provides
     * the best single solution found. It uses a boolean flag to ensure only one optimization
     * runs at a time to prevent resource conflicts.
     *
     * @param project The project containing tasks and employees to schedule
     * @param timeLimit Maximum time allowed for optimization (default: 1 minute)
     * @param callback Optional callback invoked when a solution is found
     * @return Result containing the optimal schedule or failure if scheduling is already in progress
     * @throws IllegalStateException if the scheduler is already processing another project
     */
    fun findOptimalSchedule(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Either<Throwable, SchedulerSolution> {
        if (!isProcessing.compareAndSet(false, true)) {
            return IllegalStateException("Scheduler is already processing.").left()
        }

        val result = solveOptimizationProblem(project, timeLimit, callback)
        isProcessing.set(false)
        return result
    }

    /**
     * Collects all optimal schedules discovered during the optimization process.
     *
     * This method runs the optimization and collects not just the final best solution,
     * but all intermediate solutions found throughout the solving process. This is useful
     * for analysis, comparison, or when multiple good solutions are acceptable.
     *
     * @param project The project containing tasks and employees to schedule
     * @param timeLimit Maximum time allowed for optimization (default: 1 minute)
     * @param callback Optional callback invoked for each solution found
     * @return Result containing all schedules discovered during optimization or failure details
     */
    fun collectAllOptimalSchedules(
        project: Project,
        timeLimit: Duration = 1.minutes,
        callback: (SchedulerSolution) -> Unit = {},
    ): Either<Throwable, SolutionHistory> {
        val solutions = ConcurrentLinkedQueue<SchedulerSolution>()
        val finalSolution =
            findOptimalSchedule(project, timeLimit) {
                callback(it)
                solutions.add(it)
            }
        return finalSolution.flatMap { Either.Right(SolutionHistory(solutions + it)) }
    }
}
