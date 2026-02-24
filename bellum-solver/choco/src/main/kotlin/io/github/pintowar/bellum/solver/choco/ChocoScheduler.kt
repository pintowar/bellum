package io.github.pintowar.bellum.solver.choco

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import org.chocosolver.solver.Solution
import org.chocosolver.solver.Solver
import org.chocosolver.solver.search.SearchState
import kotlin.time.Clock
import kotlin.time.Duration

class ChocoScheduler(
    override val estimator: TimeEstimator,
) : Scheduler() {
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> {
        val workers = realNumThreads(numThreads)
        return if (workers == 1) {
            singleSolve(project, timeLimit, callback)
        } else {
            parallelSolve(project, timeLimit, workers, callback)
        }
    }

    fun singleSolve(
        project: Project,
        timeLimit: Duration,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val model = ChocoModel(project, estimator)
            val solver = model.solver(timeLimit)

            val solution = model.solution()
            val initSolving = Clock.System.now()
            var bestObjective = Long.MAX_VALUE
            while (solver.solve()) {
                solution.record()
                val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()
                val currentObjective = model.objective(solution).toLong()
                if (currentObjective < bestObjective) {
                    bestObjective = currentObjective
                    model.decode(solution, currentDuration, false).onSuccess(callback)
                }
            }

            val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()
            return model.decode(solution, currentDuration, solver.searchState == SearchState.TERMINATED)
        }

    fun parallelSolve(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit = {},
    ): Result<SchedulerSolution> =
        runCatching {
            val (portfolio, chocoModels) = ChocoModel.portfolio(project, estimator, numThreads, timeLimit)

            val initSolving = Clock.System.now()
            var bestSolution: Solution? = null
            var bestModel: ChocoModel? = null
            var bestSolver: Solver? = null
            var bestObjective = Long.MAX_VALUE

            while (portfolio.solve()) {
                val finderModel = portfolio.bestModel
                val currentObjective =
                    finderModel.solver
                        .toArray()
                        .getOrNull(5)
                        ?.toLong() ?: Long.MAX_VALUE
                if (currentObjective < bestObjective) {
                    bestObjective = currentObjective
                    bestSolver = finderModel.solver
                    bestModel = chocoModels.first { it.name == finderModel.name }
                    bestSolution = bestModel.solution().record()
                    val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()
                    bestModel.decode(bestSolution, currentDuration, false).onSuccess(callback)
                }
            }

            val currentDuration = listOf(timeLimit, Clock.System.now() - initSolving).min()
            val optimal = bestSolver?.searchState == SearchState.TERMINATED

            if (bestSolution != null && bestModel != null) {
                bestModel.decode(bestSolution, currentDuration, optimal).getOrThrow()
            } else {
                val model = chocoModels.first()
                model.decode(model.solution(), currentDuration, false).getOrThrow()
            }
        }
}
