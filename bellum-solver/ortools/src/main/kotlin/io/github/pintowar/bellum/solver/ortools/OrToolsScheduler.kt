package io.github.pintowar.bellum.solver.ortools

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import kotlin.time.Clock
import kotlin.time.Duration

class OrToolsScheduler(
    override val estimator: TimeEstimator,
) : Scheduler() {
    init {
        Loader.loadNativeLibraries()
    }

    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val model = OrToolsModel(project, estimator)
            val solver = CpSolver()

            solver.parameters.maxTimeInSeconds = timeLimit.inWholeSeconds.toDouble()
            val workers = realNumThreads(numThreads)
            if (workers > 1) {
                solver.parameters.numSearchWorkers = workers
            }

            val initSolving = Clock.System.now()

            val solutionCallback =
                object : CpSolverSolutionCallback() {
                    override fun onSolutionCallback() {
                        val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()
                        val wrapper = OrToolsWrapper.SolverCallback(this)
                        model.decode(wrapper, currentDuration, false).onSuccess(callback)
                    }
                }

            val status = solver.solve(model.model, solutionCallback)
            val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()

            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                val wrapper = OrToolsWrapper.Solver(solver)
                model.decode(wrapper, currentDuration, status == CpSolverStatus.OPTIMAL).getOrThrow()
            } else {
                throw IllegalStateException("Solver did not find a valid solution (status: $status).")
            }
        }
}
