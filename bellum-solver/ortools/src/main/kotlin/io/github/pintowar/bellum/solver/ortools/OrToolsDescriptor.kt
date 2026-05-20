package io.github.pintowar.bellum.solver.ortools

import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SolverDescriptor

class OrToolsDescriptor : SolverDescriptor {
    override val name = "or-tools"
    override val description = "OR-Tools - Google Optimization Tools."

    override fun createScheduler(estimator: TimeEstimator): Scheduler = OrToolsScheduler(estimator)
}
