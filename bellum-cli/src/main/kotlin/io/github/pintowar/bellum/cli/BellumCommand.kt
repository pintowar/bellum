package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.versionOption
import java.util.Properties

class BellumCommand : CliktCommand(name = "bellum") {
    init {
        versionOption(loadVersion().removePrefix("bellum version is "))
    }

    override fun help(context: Context) = "Command line to optimizes task assignment to employees."

    override fun run() {
        // No direct execution - requires subcommand
    }

    private companion object {
        fun loadVersion(): String {
            val props =
                Thread.currentThread().contextClassLoader.let { cl ->
                    Properties().apply { load(cl.getResourceAsStream("application.properties")) }
                }
            return "bellum version is ${props["application.version"]}".trim()
        }
    }
}
