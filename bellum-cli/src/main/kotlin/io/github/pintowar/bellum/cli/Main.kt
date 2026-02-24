package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    BellumCommand()
        .subcommands(SolversCommand(), SolveCommand())
        .main(args)
}
