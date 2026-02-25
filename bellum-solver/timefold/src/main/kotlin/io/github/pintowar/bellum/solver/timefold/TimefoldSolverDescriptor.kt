package io.github.pintowar.bellum.solver.timefold

import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SolverDescriptor

class TimefoldSolverDescriptor : SolverDescriptor {
    override val name = "timefold"
    override val description = "Timefold - AI Solver"

    override fun createScheduler(estimator: TimeEstimator): Scheduler = TimefoldScheduler(estimator)
}
