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
            val solverStats =
                when (sol.stats["solver"]) {
                    "Choco Solver" -> SolverStats.ChocoSolverStats(sol.stats)
                    else -> SolverStats.UnknownSolverStats
                }
            SolutionStatsDto(
                sol.duration,
                sol.project.totalDuration() ?: kotlin.time.Duration.ZERO,
                sol.project.priorityCost,
                sol.project.isValid(),
                sol.optimal,
                solverStats,
            )
        }
    val sol =
        solutions.lastOrNull()?.let {
            SolutionSummaryDto(solutions.map { p -> ProjectDto(p.project) }, history)
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
