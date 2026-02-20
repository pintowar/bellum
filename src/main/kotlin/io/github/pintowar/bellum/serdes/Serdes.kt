package io.github.pintowar.bellum.serdes

import io.github.pintowar.bellum.core.solver.SolutionHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path
import kotlin.io.path.notExists
import kotlin.io.path.writeBytes

object Serdes {
    private val json =
        Json {
            prettyPrint = true
        }

    fun toJson(obj: SolutionSummaryDto): JsonElement = json.encodeToJsonElement(obj)
}

fun SolutionHistory.solutionAndStats(): JsonElement? {
    val history =
        solutions.map { sol ->
            val tasks = sol.project.allTasks().filterIsInstance<io.github.pintowar.bellum.core.domain.AssignedTask>()
            var priorityCost = 0L
            for (i in 0 until tasks.size) {
                for (j in i + 1 until tasks.size) {
                    val t1 = tasks[i]
                    val t2 = tasks[j]
                    if (t1.startAt < t2.startAt && t1.priority.value > t2.priority.value) {
                        priorityCost++
                    } else if (t2.startAt < t1.startAt && t2.priority.value > t1.priority.value) {
                        priorityCost++
                    }
                }
            }
            SolutionStatsDto(
                sol.duration,
                sol.project.totalDuration() ?: kotlin.time.Duration.ZERO,
                priorityCost,
                sol.project.isValid(),
                sol.optimal,
            )
        }
    val sol =
        solutions.lastOrNull()?.let {
            val solverStats =
                when (it.stats["solver"]) {
                    "Choco Solver" -> SolverStats.ChocoSolverStats(it.stats)
                    else -> SolverStats.UnknownSolverStats
                }
            SolutionSummaryDto(solutions.map { p -> ProjectDto(p.project) }, history, solverStats)
        }

    return sol?.let(Serdes::toJson)
}

fun JsonElement.export(fileName: String) {
    val file = Path(fileName)
    file.parent?.let {
        if (it.notExists()) {
            java.nio.file.Files
                .createDirectories(it)
        }
    }

    val outputHtml =
        io.github.pintowar.bellum.cli.templates.DashboardTemplate
            .generateHtml(this.toString())
    file.writeBytes(outputHtml.toByteArray())
}
