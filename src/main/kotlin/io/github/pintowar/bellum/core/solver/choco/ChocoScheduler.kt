package io.github.pintowar.bellum.core.solver.choco

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import kotlinx.datetime.Clock
import kotlin.time.Duration

class ChocoScheduler(
    override val estimator: TimeEstimator,
    private val withLexicalConstraint: Boolean = true,
) : Scheduler() {
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val model = ChocoModel(project, estimator, withLexicalConstraint)
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
}
