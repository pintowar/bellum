package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import io.github.pintowar.bellum.parser.ProjectReader
import io.github.pintowar.bellum.parser.converter.JsonToRtsConverter
import io.github.pintowar.bellum.parser.converter.RtsToJsonConverter
import java.io.File
import kotlin.system.exitProcess

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

    override fun run() {
        val currentDir = System.getProperty("user.dir")

        try {
            val parsedProject = ProjectReader.readContentFromPath(currentDir, path).getOrThrow()

            val targetFormat = if (format == "auto") detectTargetFormat(path) else format

            val converted =
                when (targetFormat) {
                    "json" -> RtsToJsonConverter().convert(parsedProject).getOrThrow()
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

            exitProcess(0)
        } catch (e: Exception) {
            echo(red(e.message ?: "Unknown error"), err = true)
            exitProcess(1)
        }
    }
}
