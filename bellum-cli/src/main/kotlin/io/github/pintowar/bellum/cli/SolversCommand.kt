package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand

class SolversCommand : CliktCommand(name = "solvers") {
    override fun run() {
        echo("Available solvers:")
        echo("")
        echo("  choco  - Choco Solver (default)")
        echo("          Constraint programming solver for scheduling optimization.")
    }
}
