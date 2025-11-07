package io.github.pintowar.bellum.cli

import io.github.pintowar.bellum.core.estimator.PearsonEstimator
import io.github.pintowar.bellum.core.parser.rts.RtsProjectReader
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.core.solver.SolutionHistory
import io.github.pintowar.bellum.core.solver.choco.ChocoScheduler
import io.github.pintowar.bellum.serdes.export
import io.github.pintowar.bellum.serdes.solutionAndStats
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

    @Option(names = ["-o", "--output"], description = ["output a detailed solution on a json file: 'output.json'"])
    private var output: Boolean = false

    @Parameters(paramLabel = "PATH", description = ["file to be solved"])
    lateinit var path: String

    private fun describe(sol: SchedulerSolution) {
        val isOptimal = if (sol.optimal) "@|bold,green optimal|@" else "@|bold,red suboptimal|@"
        val desc = Ansi.AUTO.string("[${sol.duration}]: ${sol.project.name}, ${sol.project.totalDuration()} ($isOptimal)")
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
        if (output) {
//            "png" -> result.plotHistoryAndBest().export("$currentDir/output.png")
            result.solutionAndStats()?.export("$currentDir/output.json")
        }
    }

    override fun call(): Int {
        val currentDir = System.getProperty("user.dir")
        try {
            val result = readAndSolveProject(currentDir).getOrThrow()
            writeOutput(currentDir, result)

            stdOutput.println()
            stdOutput.println(result.describeLastProject())
            return CommandLine.ExitCode.OK
        } catch (e: Exception) {
            stdError.println(Ansi.AUTO.string("@|bold,red ${e.message}|@"))
            return CommandLine.ExitCode.SOFTWARE
        }
    }
}
