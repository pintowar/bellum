package io.github.pintowar.bellum.solver.choco

import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SolverDescriptor

class ChocoSolverDescriptor : SolverDescriptor {
    override val name = "choco"
    override val description = "Choco Solver - Constraint programming solver."

    override fun createScheduler(estimator: TimeEstimator): Scheduler = ChocoScheduler(estimator)
}
