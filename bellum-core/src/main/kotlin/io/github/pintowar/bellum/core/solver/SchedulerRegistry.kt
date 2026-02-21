package io.github.pintowar.bellum.core.solver

import java.util.ServiceLoader

object SchedulerRegistry {
    private val loader = ServiceLoader.load(SolverDescriptor::class.java)

    fun availableSolvers(): List<SolverDescriptor> = loader.toList()

    fun getSolver(name: String): SolverDescriptor? =
        availableSolvers().find {
            it.name.equals(name, ignoreCase = true)
        }

    fun getSolverOrThrow(name: String): SolverDescriptor =
        getSolver(name)
            ?: throw IllegalArgumentException(
                "Unknown solver: $name. Available solvers: ${
                    availableSolvers().joinToString(", ") { it.name }
                }",
            )
}
