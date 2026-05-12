package io.github.pintowar.bellum.solver.ortools

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.IntVar

sealed interface OrToolsWrapper {
    fun getValue(value: IntVar): Long

    fun objectiveValue(): Double

    fun bestObjectiveBound(): Double

    fun numBranches(): Long

    fun numConflicts(): Long

    fun solverStatistics(): Map<String, Any> =
        mapOf(
            "solver" to "OR-Tools CP-SAT",
            "objective" to objectiveValue(),
            "nodes" to numBranches(),
            "conflicts" to numConflicts(),
            "bestBound" to bestObjectiveBound(),
        )

    class SolverCallback(
        private val solver: CpSolverSolutionCallback,
    ) : OrToolsWrapper {
        override fun getValue(value: IntVar): Long = solver.value(value)

        override fun objectiveValue(): Double = solver.objectiveValue()

        override fun bestObjectiveBound(): Double = solver.bestObjectiveBound()

        override fun numBranches(): Long = solver.numBranches()

        override fun numConflicts(): Long = solver.numConflicts()
    }

    class Solver(
        private val solver: CpSolver,
    ) : OrToolsWrapper {
        override fun getValue(value: IntVar): Long = solver.value(value)

        override fun objectiveValue(): Double = solver.objectiveValue()

        override fun bestObjectiveBound(): Double = solver.bestObjectiveBound()

        override fun numBranches(): Long = solver.numBranches()

        override fun numConflicts(): Long = solver.numConflicts()
    }
}
