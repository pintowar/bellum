package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.github.pintowar.bellum.core.estimator.PearsonEstimator
import io.github.pintowar.bellum.core.parser.rts.RtsProjectReader
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.core.solver.SolutionHistory
import io.github.pintowar.bellum.core.solver.choco.ChocoScheduler
import io.github.pintowar.bellum.plotter.cliGantt
import io.github.pintowar.bellum.serdes.export
import io.github.pintowar.bellum.serdes.solutionAndStats
import java.io.PrintStream
import java.util.Properties
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BellumCommand(
    private val stdOutput: PrintStream,
    private val stdError: PrintStream,
) : CliktCommand(name = "bellum") {
    constructor() : this(System.out, System.err)

    init {
        versionOption(loadVersion().removePrefix("bellum version is "))
    }

    override fun help(context: Context) = "Command line to optimizes task assignment to employees."

    private val timeLimit: Int by option(
        "-l",
        "--limit",
        help = "solver time limit in seconds",
    ).int().default(30)

    private val output: String? by option(
        "-o",
        "--output",
        help = "output a detailed solution on a html dashboard: 'index.html' on the given folder",
    )

    private val path: String by argument(
        "PATH",
        help = "file to be solved",
    )

    private fun formatDuration(duration: Duration?) = (duration?.let { "$it" } ?: "").padEnd(12).take(12)

    private fun describe(sol: SchedulerSolution) {
        val optimal = if (sol.optimal) green("optimal") else red("suboptimal")
        val valid = if (sol.project.isValid()) green("valid") else red("invalid")
        val status =
            when (sol.project.scheduledStatus()) {
                ProjectScheduled.NONE -> red("none")
                ProjectScheduled.PARTIAL -> yellow("partial")
                ProjectScheduled.SCHEDULED -> green("scheduled")
            }
        val desc =
            listOf(
                "[${formatDuration(sol.duration)}]:",
                sol.project.name,
                "- ${formatDuration(sol.project.totalDuration())}",
                "| $valid, $status, $optimal",
            ).joinToString(" ")

        stdOutput.println(desc)
    }

    private fun readAndSolveProject(currentDir: String): Result<SolutionHistory> =
        runCatching {
            val estimator = PearsonEstimator()
            val scheduler = ChocoScheduler(estimator)

            return RtsProjectReader
                .readContentFromPath(currentDir, path)
                .mapCatching {
                    scheduler
                        .collectAllOptimalSchedules(it, timeLimit.seconds) { sol ->
                            describe(sol)
                        }.getOrThrow()
                }
        }

    private fun writeOutput(
        currentDir: String,
        result: SolutionHistory,
    ) {
        if (output != null) {
            val out = if (output!!.endsWith(".html")) output!! else "$output/index.html"
            val absOut = if (out.startsWith("/")) out else "$currentDir/$out"
            result.solutionAndStats()?.export(absOut)
        }
    }

    override fun run() {
        val currentDir = System.getProperty("user.dir")
        try {
            val result = readAndSolveProject(currentDir).getOrThrow()
            writeOutput(currentDir, result)

            stdOutput.println()
            stdOutput.println(result.lastProject()?.cliGantt(120))
        } catch (e: Exception) {
            stdError.println(red(e.message ?: "Unknown error"))
            exitProcess(1)
        }
    }

    private companion object {
        fun loadVersion(): String {
            val props =
                Thread.currentThread().contextClassLoader.let { cl ->
                    Properties().apply { load(cl.getResourceAsStream("application.properties")) }
                }
            return "bellum version is ${props["application.version"]}".trim()
        }

        private fun bold(text: String) = "\u001b[1m$text\u001b[0m"

        private fun red(text: String) = bold("\u001b[31m$text\u001b[0m")

        private fun green(text: String) = bold("\u001b[32m$text\u001b[0m")

        private fun yellow(text: String) = bold("\u001b[33m$text\u001b[0m")
    }
}
