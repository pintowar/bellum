package io.github.pintowar.bellum.cli

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val commandLine = CommandLine(BellumCmd())
    val exitCode = commandLine.execute(*args)
    exitProcess(exitCode)
}
