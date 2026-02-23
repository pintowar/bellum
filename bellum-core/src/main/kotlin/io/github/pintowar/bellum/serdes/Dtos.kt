package io.github.pintowar.bellum.serdes

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class EmployeeDto(
    val id: String,
    val name: String,
) {
    constructor(employee: Employee) : this(employee.id().toString(), employee.name)
}

@Serializable
data class TaskDto(
    val id: String,
    val name: String,
    val priority: TaskPriority,
    val dependsOn: String? = null,
    val employee: EmployeeDto? = null,
    val startAt: Instant? = null,
    val duration: Duration? = null,
) {
    constructor(task: UnassignedTask) : this(task.id().toString(), task.description, task.priority, task.dependsOn?.id()?.toString())

    constructor(task: AssignedTask) : this(
        task.id().toString(),
        task.description,
        task.priority,
        task.dependsOn?.id()?.toString(),
        task.employee.let(::EmployeeDto),
        task.startAt,
        task.duration,
    )
}

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val kickOff: Instant,
    val employees: List<EmployeeDto>,
    val tasks: List<TaskDto>,
) {
    constructor(project: Project) : this(
        project.id().toString(),
        project.name,
        project.kickOff,
        project.allEmployees().map(::EmployeeDto),
        project.allTasks().map {
            when (it) {
                is UnassignedTask -> TaskDto(it)
                is AssignedTask -> TaskDto(it)
            }
        },
    )
}

@Serializable
data class SolutionStatsDto(
    val solverDuration: Duration,
    val maxDuration: Duration,
    val priorityCost: Long,
    val valid: Boolean,
    val optimal: Boolean,
    val solverStats: SolverStats = SolverStats.UnknownSolverStats,
)

@Serializable
data class SolutionSummaryDto(
    val solutions: List<ProjectDto>,
    val solutionHistory: List<SolutionStatsDto>,
    val solverName: String = "Unknown",
    val projectName: String = "",
)

@Serializable
sealed class SolverStats {
    @Serializable
    object UnknownSolverStats : SolverStats()

    @Serializable
    data class ChocoSolverStats(
        val modelName: String,
        val searchState: String,
        val solutions: Int,
        val objective: Long,
        val nodes: Long,
        val backtracks: Long,
        val fails: Long,
        val restarts: Long,
    ) : SolverStats() {
        constructor(stats: Map<String, Any>) : this(
            stats.getValue("model name").toString(),
            stats.getValue("search state").toString(),
            stats.getValue("solutions").toString().toInt(),
            stats.getValue("objective").toString().toLong(),
            stats.getValue("nodes").toString().toLong(),
            stats.getValue("backtracks").toString().toLong(),
            stats.getValue("fails").toString().toLong(),
            stats.getValue("restarts").toString().toLong(),
        )
    }

    @Serializable
    data class JeneticsStats(
        val fitness: Long,
        val generations: Long,
        val fitnessMin: Double,
        val fitnessMax: Double,
        val fitnessMean: Double,
        val fitnessVariance: Double,
        val alteredCount: Long,
        val killedCount: Long,
        val invalidCount: Long,
    ) : SolverStats() {
        constructor(stats: Map<String, Any>) : this(
            stats.getValue("fitness").toString().toLong(),
            stats.getValue("generations").toString().toLong(),
            stats.getValue("fitnessMin").toString().toDouble(),
            stats.getValue("fitnessMax").toString().toDouble(),
            stats.getValue("fitnessMean").toString().toDouble(),
            stats.getValue("fitnessVariance").toString().toDouble(),
            stats.getValue("alteredCount").toString().toLong(),
            stats.getValue("killedCount").toString().toLong(),
            stats.getValue("invalidCount").toString().toLong(),
        )
    }
}
