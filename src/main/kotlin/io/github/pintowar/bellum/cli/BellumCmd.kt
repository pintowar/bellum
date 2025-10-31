package io.github.pintowar.bellum.cli

import io.github.pintowar.bellum.core.estimator.PearsonEstimator
import io.github.pintowar.bellum.core.parser.rts.RtsProjectReader
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.core.solver.choco.ChocoScheduler
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "bellum",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Command line to optimizes task assignment to employees."],
)
class BellumCmd : Callable<Int> {
    private val stdOutput = System.out
    private val stdError = System.err

    @Option(names = ["-l", "--limit"], defaultValue = "30", description = ["solver time limit in seconds"])
    private var timeLimit: Int = 30

    @Option(names = ["-o", "--output"], description = ["output format [json, png]"])
    private var output: String? = null

    @Parameters(paramLabel = "PATH", description = ["file to be solved"])
    lateinit var path: String

    fun describe(sol: SchedulerSolution) {
        val isOptimal = if (sol.optimal) "@|bold,green optimal|@" else "@|bold,red suboptimal|@"
        val desc = Ansi.AUTO.string("[${sol.duration}]: ${sol.project.name}, ${sol.project.totalDuration()} ($isOptimal)")
        stdOutput.println(desc)
    }

    override fun call(): Int {
        val currentDir = System.getProperty("user.dir")
        try {
            val estimator = PearsonEstimator()
            val scheduler = ChocoScheduler(estimator)

            val result =
                RtsProjectReader
                    .readContentFromPath(currentDir, path)
                    .mapCatching { scheduler.solve(it, timeLimit.seconds, ::describe).getOrThrow() }
                    .getOrThrow()

            describe(result)
            stdOutput.println()
            stdOutput.println(result.project.describe())
            return CommandLine.ExitCode.OK
        } catch (e: Exception) {
            stdError.println(Ansi.AUTO.string("@|bold,red ${e.message}|@"))
            return CommandLine.ExitCode.SOFTWARE
        }
    }
}
