package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.github.pintowar.bellum.core.solver.SchedulerRegistry

class SolversCommand : CliktCommand(name = "solvers") {
    override fun run() {
        val solvers = SchedulerRegistry.availableSolvers()
        echo("Available solvers:")
        echo("")
        solvers.forEach { solver ->
            val defaultMark = if (solver.name == "choco") " (default)" else ""
            echo("  ${solver.name}$defaultMark  - ${solver.description}")
        }
    }
}
