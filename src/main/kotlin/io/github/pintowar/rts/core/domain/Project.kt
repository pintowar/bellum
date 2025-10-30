package io.github.pintowar.rts.core.domain

import io.github.pintowar.rts.core.util.Helper
import io.konform.validation.Validation
import io.konform.validation.andThen
import kotlinx.datetime.Instant
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import java.util.UUID
import kotlin.time.Duration

enum class ProjectScheduled { NONE, PARTIAL, SCHEDULED }

@JvmInline
value class ProjectId(
    private val value: UUID,
) {
    constructor() : this(Helper.uuidV7())

    operator fun invoke() = value
}

class Project private constructor(
    val id: ProjectId,
    val name: String,
    val kickOff: Instant,
    private val employees: Set<Employee>,
    private val tasks: Set<Task>,
) {
    companion object {
        private val initValidator =
            Validation<Project> {
                Project::hasCircularTaskDependency {
                    constrain("Circular task dependency found {value}.") { it.isEmpty() }
                }

                Project::hasUnknownEmployees {
                    constrain("Some tasks are assigned to employees out of the project: {value}.") { it.isEmpty() }
                }

                Project::hasMissingTaskDependencies {
                    constrain("Following task dependencies were not found: {value}.") { it.isEmpty() }
                }
            }

        private val validator =
            initValidator andThen
                Validation<Project> {
                    Project::employeesWithOverlap {
                        constrain("Overlapped tasks for employee: {value}.") { it.isEmpty() }
                    }

                    Project::precedenceBroken {
                        constrain("Precedences broken: {value}.") { it.isEmpty() }
                    }
                }

        operator fun invoke(
            id: UUID,
            name: String,
            kickOff: Instant,
            employees: Set<Employee>,
            tasks: Set<Task>,
        ): Result<Project> =
            runCatching {
                Project(ProjectId(id), name, kickOff, employees, tasks).also {
                    val res = initValidator.validate(it)
                    if (!res.isValid) throw ValidationException(res.errors)
                }
            }

        operator fun invoke(
            name: String,
            kickOff: Instant,
            employees: Set<Employee>,
            tasks: Set<Task>,
        ): Result<Project> = invoke(ProjectId()(), name, kickOff, employees, tasks)
    }

    fun allEmployees() = employees.toList()

    fun allTasks() = tasks.toList()

    fun scheduledStatus(): ProjectScheduled {
        val numAssignedTask = tasks.count { it.isAssigned() }
        return when (numAssignedTask) {
            0 -> ProjectScheduled.NONE
            tasks.size -> ProjectScheduled.SCHEDULED
            else -> ProjectScheduled.PARTIAL
        }
    }

    fun endsAt(): Instant? = allTasks().mapNotNull { it.endsAt() }.maxOrNull()

    fun totalDuration(): Duration? = endsAt()?.let { it - kickOff }

    fun validate() = validator.validate(this)

    fun isValid() = validate().isValid

    fun replace(
        name: String? = null,
        kickOff: Instant? = null,
        employees: Set<Employee>? = null,
        tasks: Set<Task>? = null,
    ): Result<Project> =
        invoke(
            id = id(),
            name = name ?: this.name,
            kickOff = kickOff ?: this.kickOff,
            employees = employees ?: this.employees,
            tasks = tasks ?: this.tasks,
        )

    // validations
    fun employeesWithOverlap(): List<String> =
        tasks
            .asSequence()
            .filter { it.isAssigned() }
            .map { it as AssignedTask }
            .groupBy({ it.employee })
            .map { (emp, tasks) -> emp.name to tasks.hasOverlappingIntervals() }
            .filter { (_, overs) -> overs }
            .map { (emp, _) -> emp }

    fun precedenceBroken(): List<String> =
        tasks
            .asSequence()
            .filter { it.isAssigned() && (it.dependsOn?.isAssigned() ?: false) }
            .map { it as AssignedTask to it.dependsOn as AssignedTask }
            .filter { (a, b) -> a.startAt < b.endsAt }
            .map { (a, b) -> "${a.employee.name} (start: ${a.startAt}) < ${b.employee.name} (end: ${b.startAt})" }
            .toList()

    fun hasCircularTaskDependency(): String {
        val graph = DefaultDirectedGraph<TaskId, DefaultEdge>(DefaultEdge::class.java)
        val byIds = tasks.associateBy { it.id }

        val precedence =
            tasks
                .asSequence()
                .filter { it.dependsOn != null }
                .map { it.id to it.dependsOn!!.id }

        precedence.flatMap { it.toList() }.toSet().forEach { graph.addVertex(it) }
        precedence.forEach { (a, b) -> graph.addEdge(a, b) }

        val cycle = CycleDetector(graph).findCycles().map { byIds.getValue(it).description }.sorted()
        return (cycle + cycle.take(1)).joinToString(" - ")
    }

    fun hasUnknownEmployees(): List<String> {
        val projectEmployees = employees.map { it.id }.toSet()
        val assignedEmployees = tasks.filter { it.isAssigned() }.map { it as AssignedTask }.map { it.employee }
        return assignedEmployees.filter { it.id !in projectEmployees }.map { it.name }
    }

    fun hasMissingTaskDependencies(): String {
        val taskIds = tasks.associateBy { it.id }
        val dependenciesIds = tasks.mapNotNull { it.dependsOn }.associateBy { it.id }
        return (dependenciesIds.keys - taskIds.keys).joinToString(", ") { dependenciesIds.getValue(it).description }
    }
}
