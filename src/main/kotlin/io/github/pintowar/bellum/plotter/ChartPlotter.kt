package io.github.pintowar.bellum.plotter

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.solver.SolutionHistory
import org.jetbrains.letsPlot.Figure
import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.geom.geomRect
import org.jetbrains.letsPlot.geom.geomText
import org.jetbrains.letsPlot.gggrid
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.label.xlab
import org.jetbrains.letsPlot.label.ylab
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.scale.scaleFillManual
import org.jetbrains.letsPlot.scale.scaleYContinuous
import org.jetbrains.letsPlot.themes.flavorSolarizedDark

object ChartPlotter {
    internal val width = 1200
    internal val height = 350
    private val tHeight = 50

    private fun employeesHeights(employees: List<String>) =
        employees
            .distinct()
            .sorted()
            .let { it.zip(it.scan(0) { acc, _ -> acc + tHeight * 2 }) }
            .toMap()

    private fun projectAssignedTasks(project: Project) = project.allTasks().filter { it.isAssigned() }.map { it as AssignedTask }

    fun generateTable(project: Project): Map<String, List<*>> {
        val assignedTasks = projectAssignedTasks(project)
        val job = assignedTasks.map { it.description }
        val employees = assignedTasks.map { it.employee.name }
        val start = assignedTasks.map { (it.startAt - project.kickOff).inWholeMinutes }
        val end = assignedTasks.map { (it.endsAt - project.kickOff).inWholeMinutes }
        val priority = assignedTasks.map { it.priority.toString() }

        val yEmployees = employeesHeights(employees)
        val ymin = employees.mapNotNull { yEmployees.getValue(it) - tHeight / 2 }
        val ymax = ymin.map { it + tHeight }

        return mapOf(
            "job" to job,
            "employee" to employees,
            "ymin" to ymin,
            "ymax" to ymax,
            "start" to start,
            "end" to end,
            "labelx" to start.zip(end).map { (s, e) -> s + (e - s) / 2 },
            "labely" to ymin.map { it + tHeight / 2 },
            "priority" to priority,
        )
    }

    fun plotGantt(project: Project): Plot {
        val duration = project.totalDuration()?.inWholeMinutes ?: 0
        val data = generateTable(project)
        val employees = projectAssignedTasks(project).map { it.employee.name }
        val yEmployees = employeesHeights(employees)

        var p = letsPlot(data)
        p +=
            geomRect(size = 0.5, alpha = 1, linetype = "solid", color = "black") {
                xmin = "start"
                xmax = "end"
                ymin = "ymin"
                ymax = "ymax"
                fill = "priority"
            }
        p +=
            scaleFillManual(
                name = "Priority",
                values =
                    mapOf(
                        "CRITICAL" to "red",
                        "MAJOR" to "blue",
                        "MINOR" to "green",
                    ),
            )
        p +=
            scaleYContinuous(
                breaks = yEmployees.values.toList(),
                labels = yEmployees.keys.toList(),
            )
        p +=
            geomText(color = "white") {
                x = "labelx"
                y = "labely"
                label = "job"
            }
        p +=
            labs(
                x = "Time (minutes)",
                y = "Employees",
                title = "Total duration: $duration minutes",
            )
        p += ggsize(width, height)
        p += flavorSolarizedDark()

        return p
    }

    fun plotSolutionHistory(history: SolutionHistory): Plot {
        val (time, duration) =
            history.solutions
                .map { it.duration.inWholeSeconds to it.project.totalDuration()?.inWholeMinutes }
                .unzip()

        val data =
            mapOf(
                "time" to time,
                "duration" to duration,
            )
        var p =
            letsPlot(data) {
                x = "time"
                y = "duration"
            }
        p += ggtitle("Total Solutions: ${history.solutions.size}")
        p += xlab("Time (secs)")
        p += ylab("Max duration (minutes)")
        p += geomLine()

        p += ggsize(width, height)
        p += flavorSolarizedDark()

        return p
    }
}

fun Project.plotGantt() = ChartPlotter.plotGantt(this)

fun SolutionHistory.plotSolutionHistory() = ChartPlotter.plotSolutionHistory(this)

fun SolutionHistory.plotHistoryAndBest(): Figure {
    val historyPlot = this.plotSolutionHistory()
    val projectPlot =
        this.solutions
            .last()
            .project
            .plotGantt()
    return gggrid(listOf(projectPlot, historyPlot), ncol = 1) + ggsize(ChartPlotter.width, ChartPlotter.height * 2)
}

fun Figure.export(fileName: String) {
    ggsave(this, fileName)
}
