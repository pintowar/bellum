package io.github.pintowar.bellum.plotter

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.solver.SolutionHistory
import org.jetbrains.letsPlot.Figure
import org.jetbrains.letsPlot.asDiscrete
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.geom.geomSegment
import org.jetbrains.letsPlot.geom.geomText
import org.jetbrains.letsPlot.gggrid
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.label.xlab
import org.jetbrains.letsPlot.label.ylab
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.themes.flavorSolarizedDark

object Plotter {
    private val width = 1200
    private val height = 350

    fun generateTable(project: Project): Map<String, List<*>> {
        val assignedTasks = project.allTasks().filter { it.isAssigned() }.map { it as AssignedTask }

        return mapOf(
            "job" to assignedTasks.map { it.description },
            "employee" to assignedTasks.map { it.employee.name },
            "start" to assignedTasks.map { (it.startAt - project.kickOff).inWholeMinutes },
            "end" to assignedTasks.map { (it.endsAt - project.kickOff).inWholeMinutes },
            "priority" to assignedTasks.map { it.priority.toString() },
        )
    }

    fun plotGantt(project: Project): Plot {
        val duration = project.totalDuration()?.inWholeMinutes ?: 0
        val data = generateTable(project)

        var p =
            letsPlot(data) {
                x = "start"
                y = "employee"
            }
        p += ggtitle("Total duration: $duration minutes")
        p += xlab("Time (minutes)")
        p += ylab("Employees")
        p +=
            geomSegment(
                size = 15.0, // Adjust the thickness of the bars
                showLegend = true, // Hide legend for colors
            ) {
                x = "start"
                xend = "end"
                y = "employee"
                yend = "employee"
                color = asDiscrete("priority")
            }
        p +=
            geomText(
                color = "white", // Set text color to white for better contrast
                hjust = "left",
                nudgeX = 0.8,
            ) {
                label = "job"
            }
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

fun Project.plotGantt() = Plotter.plotGantt(this)

fun SolutionHistory.plotSolutionHistory() = Plotter.plotSolutionHistory(this)

fun SolutionHistory.plotHistoryAndBest(): Figure {
    val historyPlot = this.plotSolutionHistory()
    val projectPlot =
        this.solutions
            .last()
            .project
            .plotGantt()
    return gggrid(listOf(projectPlot, historyPlot), ncol = 1)
}
