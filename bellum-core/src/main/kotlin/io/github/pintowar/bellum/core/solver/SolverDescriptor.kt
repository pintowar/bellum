package io.github.pintowar.bellum.core.solver

import io.github.pintowar.bellum.core.estimator.TimeEstimator

interface SolverDescriptor {
    val name: String

    val description: String

    fun createScheduler(estimator: TimeEstimator): Scheduler
}
