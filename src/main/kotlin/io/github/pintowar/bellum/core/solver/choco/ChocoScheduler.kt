package io.github.pintowar.bellum.core.solver.choco

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import kotlinx.datetime.Clock
import org.chocosolver.solver.ParallelPortfolio
import org.chocosolver.solver.Solution
import kotlin.time.Duration

class ChocoScheduler(
    override val estimator: TimeEstimator,
    private val numThreads: Int = 1.coerceAtLeast((Runtime.getRuntime().availableProcessors() * 0.9).toInt()),
) : Scheduler() {
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        if (numThreads == 1) {
            singleSolve(project, timeLimit, callback)
        } else {
            parallelSolve(project, timeLimit, numThreads, callback)
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
            while (solver.solve()) {
                solution.record()
                val currentDuration = (Clock.System.now() - initSolving)
                model.decode(solution, currentDuration, false).onSuccess(callback)
            }

            val currentDuration = Clock.System.now() - initSolving
            return model.decode(solution, currentDuration, !solver.isStopCriterionMet)
        }

    fun parallelSolve(
        project: Project,
        timeLimit: Duration,
        numThreads: Int = Runtime.getRuntime().availableProcessors(),
        callback: (SchedulerSolution) -> Unit = {},
    ): Result<SchedulerSolution> =
        runCatching {
            val portfolio = ParallelPortfolio()
            val chocoModels = List(numThreads) { ChocoModel(project, estimator, it) }
            chocoModels.forEach { chocoModel ->
                chocoModel.model.solver.limitTime(timeLimit.inWholeMilliseconds)
                portfolio.addModel(chocoModel.model)
            }

            val initSolving = Clock.System.now()
            var bestSolution: Solution? = null
            var bestModel: ChocoModel? = null

            while (portfolio.solve()) {
                val finderModel = portfolio.bestModel
                bestModel = chocoModels.find { it.model.name == finderModel.name }
                if (bestModel != null) {
                    bestSolution = Solution(finderModel).record()
                    val currentDuration = Clock.System.now() - initSolving
                    bestModel.decode(bestSolution, currentDuration, false).onSuccess(callback)
                }
            }

            val currentDuration = Clock.System.now() - initSolving
            val optimal = portfolio.models.all { it.solver.isStopCriterionMet }

            if (bestSolution != null && bestModel != null) {
                bestModel.decode(bestSolution, currentDuration, !optimal).getOrThrow()
            } else {
                chocoModels.first().decode(chocoModels.first().solution(), currentDuration, false).getOrThrow()
            }
        }
}
