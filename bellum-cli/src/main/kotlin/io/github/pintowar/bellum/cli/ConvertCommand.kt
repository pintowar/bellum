package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import io.github.pintowar.bellum.core.estimator.EstimationMatrix
import io.github.pintowar.bellum.core.io.ParsedProject
import io.github.pintowar.bellum.estimator.PearsonEstimator
import io.github.pintowar.bellum.io.converter.JsonToRtsConverter
import io.github.pintowar.bellum.io.converter.RtsToJsonConverter
import io.github.pintowar.bellum.io.reader.ProjectReader
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit

class ConvertCommand : CliktCommand(name = "convert") {
    private val output: String? by option(
        "-o",
        "--output",
        help = "output file path (default: stdout)",
    )

    private val format: String by option(
        "-f",
        "--format",
        help = "target format (auto-detected if not specified)",
    ).choice("json", "rts").default("auto")

    private val recalcMatrix: Boolean by option(
        "-m",
        "--recalc-matrix",
        help = "force recalculation of the estimation matrix",
    ).flag(default = false)

    private val path: String by argument(
        "PATH",
        help = "file to be converted",
    )

    private fun detectTargetFormat(sourcePath: String): String {
        val extension = File(sourcePath).extension.lowercase()
        return when (extension) {
            "json" -> "rts"
            else -> "json"
        }
    }

    private fun bold(text: String) = TextStyles.bold(text)

    private fun green(text: String) = bold(TextColors.green(text))

    private fun red(text: String) = bold(TextColors.red(text))

    private fun recalculateMatrix(parsedProject: ParsedProject): ParsedProject {
        val estimator = PearsonEstimator()
        val matrix = EstimationMatrix(parsedProject.project, estimator)
        val employees = parsedProject.project.allEmployees()
        val tasks = parsedProject.project.allTasks()

        return parsedProject.copy(
            estimationMatrix =
                employees.map { emp ->
                    tasks.map { task ->
                        val duration: Duration = matrix.duration(emp.id, task.id).getOrThrow()
                        duration.toLong(DurationUnit.MINUTES)
                    }
                },
        )
    }

    override fun run() {
        val currentDir = System.getProperty("user.dir")

        try {
            var parsedProject = ProjectReader.readContentFromPath(currentDir, path).getOrThrow()

            if (recalcMatrix) {
                parsedProject = recalculateMatrix(parsedProject)
            }

            val targetFormat = if (format == "auto") detectTargetFormat(path) else format

            val converted =
                when (targetFormat) {
                    "json" -> RtsToJsonConverter(omitSkills = recalcMatrix).convert(parsedProject).getOrThrow()
                    "rts" -> JsonToRtsConverter().convert(parsedProject).getOrThrow()
                    else -> throw IllegalArgumentException("Unsupported target format: $targetFormat")
                }

            if (output != null) {
                val outPath = if (output!!.startsWith("/")) output!! else "$currentDir/$output"
                File(outPath).writeText(converted)
                echo(green("Converted to $targetFormat: $outPath"))
            } else {
                echo(converted)
            }
        } catch (e: Exception) {
            echo(red(e.message ?: "Unknown error"), err = true)
            throw ProgramResult(1)
        }
    }
}
