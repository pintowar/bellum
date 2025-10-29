package io.github.pintowar.rts.cli

import org.koin.core.context.startKoin
import picocli.CommandLine

fun main(args: Array<String>) {
    val koin =
        startKoin {
            modules(appModule)
        }.koin

    val commandLine = CommandLine(SolveCmd(), KoinFactory(koin))
    val exitCode = commandLine.execute(*args)
    System.exit(exitCode)
}
