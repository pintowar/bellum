package io.github.pintowar.bellum.plotter

import io.github.pintowar.bellum.core.domain.Project
import kotlin.math.roundToInt

object CliPlotter {
    fun generateCliPlot(
        project: Project,
        width: Int = 100,
    ): String {
        val projectDuration = project.totalDuration()?.inWholeMinutes?.toInt() ?: 0
        if (projectDuration == 0) return "No tasks assigned to show a schedule."

        val scale = projectDuration.toDouble() / width

        // --- ANSI Colors ---
        val bgRed = "\u001b[41m"
        val bgBlue = "\u001b[44m"
        val bgGreen = "\u001b[42m"
        val bgReset = "\u001b[0m"
        val txtWhite = "\u001b[97m"
        val txtGrey = "\u001b[90m"

        val colors =
            mapOf(
                "MINOR" to bgGreen,
                "MAJOR" to bgBlue,
                "CRITICAL" to bgRed,
            )

        val output = StringBuilder()

        output.appendLine("\nTotal duration: $projectDuration minutes (Scale: ~%.1f min/char)".format(scale))

        // --- Helper Function to create grid lines ---
        val rulerStep =
            if (projectDuration > 500) {
                100
            } else if (projectDuration > 100) {
                50
            } else {
                10
            }
        val rulerMarks = (0..projectDuration step rulerStep).toList()

        fun isGridLine(charIndex: Int): Boolean = rulerMarks.any { (it / scale).toInt() == charIndex }

        // 1. Render Top Ruler
        val headerBuilder = StringBuilder("        ") // Padding for names (8 spaces)

        var lastPos = -1
        for (mark in rulerMarks) {
            val pos = (mark / scale).toInt()
            if (pos <= lastPos) continue // avoid printing on top of previous
            val currentLen = headerBuilder.length - 8 // Adjust for initial padding
            if (pos > currentLen) {
                headerBuilder.append(" ".repeat(pos - currentLen))
            }
            headerBuilder.append(mark)
            lastPos = headerBuilder.length - 8
        }
        output.appendLine(headerBuilder.toString())

        val rulerLine = StringBuilder("        ")
        lastPos = -1
        for (mark in rulerMarks) {
            val pos = (mark / scale).toInt()
            if (pos <= lastPos) continue
            val currentLen = rulerLine.length - 8
            if (pos > currentLen) {
                rulerLine.append(" ".repeat(pos - currentLen))
            }
            rulerLine.append("|")
            lastPos = rulerLine.length - 8
        }

        output.appendLine(rulerLine.toString())

        // 2. Render Rows
        val employeesTasks = project.assignedTasks()
        for ((employee, tasks) in employeesTasks) {
            val finalLine = StringBuilder()
            var currentCharIdx = 0

            val sortedTasks = tasks.sortedBy { it.startAt }

            for (t in sortedTasks) {
                val taskStart = (t.startAt - project.kickOff).inWholeMinutes.toInt()
                val taskDuration = t.duration.inWholeMinutes.toInt()

                val taskStartIdx = (taskStart / scale).toInt()
                var taskWidth = (taskDuration / scale).toInt()
                if (taskWidth < 1) taskWidth = 1

                // A. Fill Gap (with grid lines)
                if (currentCharIdx < taskStartIdx) {
                    for (i in currentCharIdx until taskStartIdx) {
                        val char = if (isGridLine(i)) "$txtGrey|$bgReset" else " "
                        finalLine.append(char)
                    }
                    currentCharIdx = taskStartIdx
                }

                // B. Draw Task
                // Determine label text
                val taskId = t.description.substringAfterLast('-').take(8)
                var label = if (taskWidth < 6) "T$taskId" else "Task $taskId"
                if (label.length > taskWidth) {
                    label = label.take(taskWidth) // Truncate
                }

                // Center text manually
                val paddingTotal = taskWidth - label.length
                val paddingLeft = paddingTotal / 2
                val paddingRight = paddingTotal - paddingLeft

                val textContent = " ".repeat(paddingLeft) + label + " ".repeat(paddingRight)

                val colorCode = colors[t.priority.name] ?: bgReset
                finalLine.append("$colorCode$txtWhite$textContent$bgReset")

                currentCharIdx += taskWidth
            }

            // C. Fill remaining line (with grid lines)
            if (currentCharIdx < width) {
                for (i in currentCharIdx until width) {
                    val char = if (isGridLine(i)) "$txtGrey|$bgReset" else " "
                    finalLine.append(char)
                }
            }

            // Print the row
            // %-8s aligns the name to the left with 8 chars width
            output.appendLine("%-8s %s".format(employee.name.take(8), finalLine.toString()))

            // Optional: Separator line
            output.appendLine(" ".repeat(8) + "$txtGrey" + "-".repeat(width) + bgReset)
        }

        output.appendLine(
            "\nLegend: [${colors["MINOR"]} MINOR $bgReset] [${colors["MAJOR"]} MAJOR $bgReset] [${colors["CRITICAL"]} CRITICAL $bgReset]",
        )

        return output.toString()
    }
}

fun Project.cliGantt(width: Int = 100) = CliPlotter.generateCliPlot(this, width)
