package io.github.pintowar.rts.core.plotter

import io.github.pintowar.rts.core.domain.AssignedTask
import io.github.pintowar.rts.core.domain.Project
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.letsPlot.asDiscrete
import org.jetbrains.letsPlot.geom.geomSegment
import org.jetbrains.letsPlot.geom.geomText
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.label.xlab
import org.jetbrains.letsPlot.label.ylab
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.themes.flavorSolarizedDark

object Plotter {
    fun generateTable(
        project: Project,
        from: Instant,
    ): Map<String, List<*>> {
        val assignedTasks = project.allTasks().filter { it.isAssigned() }.map { it as AssignedTask }

        return mapOf(
            "job" to assignedTasks.map { it.description },
            "employee" to assignedTasks.map { it.employee.name },
            "start" to assignedTasks.map { (it.startAt - from).inWholeMinutes },
            "end" to assignedTasks.map { (it.endsAt - from).inWholeMinutes },
            "priority" to assignedTasks.map { it.priority.toString() },
        )
    }

    fun plotGantt(project: Project): Plot = plotGantt(project, from = Clock.System.now())

    fun plotGantt(
        project: Project,
        from: Instant,
    ): Plot {
        val duration = project.endsAt()?.let { (it - from).inWholeMinutes } ?: 0
        val data = generateTable(project, from)

        var p =
            letsPlot(data) {
                x = "start"
                y = "employee"
            }
        p += ggtitle("Total duration: $duration minutes")
        p += xlab("Time (minutes)")
        p += ylab("Employees")
        p += geomSegment(
            size = 15.0, // Adjust the thickness of the bars
            showLegend = true, // Hide legend for colors
        ) {
            x = "start"
            xend = "end"
            y = "employee"
            yend = "employee"
            color = asDiscrete("priority")
        } +
            geomText(
                color = "white", // Set text color to white for better contrast
                hjust = "left",
                nudgeX = 0.8,
            ) {
                label = "job"
            }
        p += ggsize(1200, 350)
        p += flavorSolarizedDark()

        return p
    }
}
