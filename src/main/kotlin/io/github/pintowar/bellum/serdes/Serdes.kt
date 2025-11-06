package io.github.pintowar.bellum.serdes

import io.github.pintowar.bellum.core.solver.SolutionHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path
import kotlin.io.path.createFile
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
    val history = solutions.map { SolutionStatsDto(it.duration, it.project.totalDuration()!!, it.project.isValid(), it.optimal) }
    val sol =
        solutions.lastOrNull()?.let {
            val solverStats =
                when (it.stats["solver"]) {
                    "Choco Solver" -> SolverStats.ChocoSolverStats(it.stats)
                    else -> SolverStats.UnknownSolverStats
                }
            SolutionSummaryDto(ProjectDto(it.project), history, solverStats)
        }

    return sol?.let(Serdes::toJson)
}

fun JsonElement.export(fileName: String) {
    val file = Path(fileName)
    if (file.notExists()) {
        file.createFile()
    }
    this.toString().toByteArray().let(file::writeBytes)
}
