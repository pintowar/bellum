package io.github.pintowar.bellum.plotter

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import kotlin.math.min

object CliPlotter {
    // --- ANSI Colors ---
    private const val BG_RED = "\u001b[41m"
    private const val BG_BLUE = "\u001b[44m"
    private const val BG_GREEN = "\u001b[42m"
    private const val BG_RESET = "\u001b[0m"
    private const val TXT_WHITE = "\u001b[97m"
    private const val TXT_GREY = "\u001b[90m"

    private val COLORS =
        mapOf(
            "MINOR" to BG_GREEN,
            "MAJOR" to BG_BLUE,
            "CRITICAL" to BG_RED,
        )

    private const val NAME_PADDING = 8
    private const val MIN_TASK_WIDTH = 1
    private const val TASK_LABEL_MAX_LENGTH = 8
    private const val COMPACT_LABEL_THRESHOLD = 6

    private const val RULER_STEP_LARGE_DURATION = 500
    private const val RULER_STEP_MEDIUM_DURATION = 100
    private const val RULER_STEP_LARGE = 100
    private const val RULER_STEP_MEDIUM = 50
    private const val RULER_STEP_SMALL = 10

    fun generateCliPlot(
        project: Project,
        width: Int = 100,
    ): String {
        val projectDuration = project.totalDuration()?.inWholeMinutes?.toInt() ?: 0
        if (projectDuration == 0) return "No tasks assigned to show a schedule."

        val scale = projectDuration.toDouble() / width

        val rulerStep =
            when {
                projectDuration > RULER_STEP_LARGE_DURATION -> RULER_STEP_LARGE
                projectDuration > RULER_STEP_MEDIUM_DURATION -> RULER_STEP_MEDIUM
                else -> RULER_STEP_SMALL
            }
        val rulerMarks = (0..projectDuration step rulerStep)
        val gridLineIndexes = rulerMarks.map { (it / scale).toInt() }.toSet()

        val (rulerHeader, rulerLine) = buildRuler(rulerMarks, scale, width)

        val namePadding = " ".repeat(NAME_PADDING)

        val body =
            project
                .assignedTasks()
                .entries
                .joinToString("\n") { (employee, tasks) ->
                    val employeeRow = buildEmployeeRow(tasks, project, scale, width, gridLineIndexes)
                    val separator = "$namePadding$TXT_GREY${"-".repeat(width)}$BG_RESET"
                    "${employeeName(employee)} $employeeRow\n$separator"
                }

        val legend =
            "\nLegend: [${COLORS["MINOR"]} MINOR $BG_RESET] [${COLORS["MAJOR"]} MAJOR $BG_RESET] [${COLORS["CRITICAL"]} CRITICAL $BG_RESET]"

        return buildString {
            appendLine()
            appendLine("Total duration: $projectDuration minutes")
            appendLine("$namePadding$rulerHeader")
            appendLine("$namePadding$rulerLine")
            appendLine(body)
            appendLine(legend)
        }
    }

    private fun employeeName(employee: Employee): String =
        if (employee.name.length <= NAME_PADDING) {
            employee.name.padEnd(NAME_PADDING)
        } else {
            "E${employee.name.takeLast(NAME_PADDING - 1)}"
        }

    private fun buildRuler(
        rulerMarks: IntProgression,
        scale: Double,
        width: Int,
    ): Pair<String, String> {
        val header = StringBuilder(width)
        val line = StringBuilder(width)
        var lastPos = -1

        rulerMarks.forEach { mark ->
            val pos = (mark / scale).toInt()
            val markStr = mark.toString()
            if (pos in (lastPos + 1)..<width) {
                if (header.length < pos) header.append(" ".repeat(pos - header.length))
                header.append(markStr)

                if (line.length < pos) line.append(" ".repeat(pos - line.length))
                line.append("|")

                lastPos = header.length - 1
            }
        }
        return header.toString() to line.toString()
    }

    private fun buildEmployeeRow(
        tasks: List<AssignedTask>,
        project: Project,
        scale: Double,
        width: Int,
        gridLineIndexes: Set<Int>,
    ): String {
        val line = StringBuilder(width)
        var currentCharIdx = 0

        tasks.sortedBy { it.startAt }.forEach { task ->
            if (currentCharIdx >= width) return@forEach

            val taskStart = (task.startAt - project.kickOff).inWholeMinutes.toInt()
            val taskStartIdx = (taskStart / scale).toInt()

            val gapEnd = min(taskStartIdx, width)
            (currentCharIdx until gapEnd).forEach { i ->
                line.append(if (i in gridLineIndexes) "$TXT_GREY|$BG_RESET" else " ")
            }
            currentCharIdx = gapEnd
            if (currentCharIdx >= width) return@forEach

            val taskDuration = task.duration.inWholeMinutes.toInt()
            val taskWidth = (taskDuration / scale).toInt().coerceAtLeast(MIN_TASK_WIDTH)
            val effectiveTaskWidth = min(taskWidth, width - currentCharIdx)

            val taskContent = buildTaskContent(task, effectiveTaskWidth)
            line.append(taskContent)

            currentCharIdx += effectiveTaskWidth
        }

        (currentCharIdx until width).forEach { i ->
            line.append(if (i in gridLineIndexes) "$TXT_GREY|$BG_RESET" else " ")
        }

        return line.toString()
    }

    private fun buildTaskContent(
        task: AssignedTask,
        taskWidth: Int,
    ): String {
        val taskLabel = task.description.substringAfterLast('-').take(TASK_LABEL_MAX_LENGTH)
        val label = if (taskWidth < COMPACT_LABEL_THRESHOLD) "T$taskLabel" else "Task $taskLabel"
        val truncatedLabel = label.take(taskWidth)

        val paddingTotal = taskWidth - truncatedLabel.length
        val paddingLeft = paddingTotal / 2
        val paddingRight = paddingTotal - paddingLeft

        val textContent = " ".repeat(paddingLeft) + truncatedLabel + " ".repeat(paddingRight)

        val colorCode = COLORS[task.priority.name] ?: BG_RESET
        return "$colorCode$TXT_WHITE$textContent$BG_RESET"
    }
}

fun Project.cliGantt(width: Int = 100) = CliPlotter.generateCliPlot(this, width)
