package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerRegistry
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.core.solver.SolutionHistory
import io.github.pintowar.bellum.estimator.CustomEstimator
import io.github.pintowar.bellum.estimator.PearsonEstimator
import io.github.pintowar.bellum.parser.ProjectReader
import io.github.pintowar.bellum.plotter.cliGantt
import io.github.pintowar.bellum.serdes.export
import io.github.pintowar.bellum.serdes.solutionAndStats
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SolveCommand : CliktCommand(name = "solve") {
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

    private val solver: String by option(
        "-s",
        "--solver",
        help = "solver to use (default: choco)",
    ).default("choco")

    private val parallel: Int by option(
        "-p",
        "--parallel",
        help = "number of threads for parallel solver (-1 for auto, 1 for single-thread, default: -1)",
    ).int().default(-1)

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

        echo(desc)
    }

    private fun getScheduler(estimator: TimeEstimator): Scheduler = SchedulerRegistry.getSolverOrThrow(solver).createScheduler(estimator)

    private fun readAndSolveProject(currentDir: String): Result<SolutionHistory> =
        runCatching {
            val parsedProject =
                ProjectReader.readContentFromPath(currentDir, path).getOrThrow()

            val estimator =
                parsedProject.estimationMatrix?.let { matrix ->
                    CustomEstimator(parsedProject.project, matrix)
                } ?: PearsonEstimator()

            val scheduler = getScheduler(estimator)

            scheduler
                .collectAllOptimalSchedules(parsedProject.project, timeLimit.seconds, parallel) { sol ->
                    describe(sol)
                }.getOrThrow()
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

            echo()
            echo(result.lastProject()?.cliGantt(120))
            exitProcess(0)
        } catch (e: Exception) {
            echo(red(e.message ?: "Unknown error"), err = true)
            exitProcess(1)
        }
    }

    private companion object {
        private fun bold(text: String) = TextStyles.bold(text)

        private fun red(text: String) = bold(TextColors.red(text))

        private fun green(text: String) = bold(TextColors.green(text))

        private fun yellow(text: String) = bold(TextColors.yellow(text))
    }
}
