package io.github.pintowar.bellum.solver.jenetics

import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SolverDescriptor

class JeneticsSolverDescriptor : SolverDescriptor {
    override val name = "jenetics"
    override val description = "Jenetics - Genetic Algorithm Library."

    override fun createScheduler(estimator: TimeEstimator): Scheduler = JeneticsScheduler(estimator)
}
