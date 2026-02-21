package io.github.pintowar.bellum.core.solver

interface SolverDescriptor {
    val name: String

    val description: String

    fun createScheduler(): Scheduler
}
