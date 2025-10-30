package io.github.pintowar.bellum.cli

import picocli.CommandLine

fun main(args: Array<String>) {
    val commandLine = CommandLine(BellumCmd())
    val exitCode = commandLine.execute(*args)
    System.exit(exitCode)
}
