package io.github.pintowar.bellum.core.domain

import io.github.pintowar.bellum.core.util.Helper
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

    fun assignedTasks() =
        tasks
            .asSequence()
            .filter { it.isAssigned() }
            .map { it as AssignedTask }
            .groupBy({ it.employee })

    fun describe(): String =
        """
        |Project: $name (starting at $kickOff). Max duration: ${totalDuration()}.
        |-------
        ${assignedTasks().map { (emp, tasks) ->
            "|${emp.name}: ${tasks.map { "${it.description} (${it.priority}) - ${it.duration}" }}"
        }.joinToString("\n")}
    """.trimMargin("|")

    fun improvedDescription(width: Int = 100): String {
        val projectDuration = totalDuration()?.inWholeMinutes?.toInt() ?: 0
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
        val employeesTasks = assignedTasks()
        for ((employee, tasks) in employeesTasks) {
            val finalLine = StringBuilder()
            var currentCharIdx = 0

            val sortedTasks = tasks.sortedBy { it.startAt }

            for (t in sortedTasks) {
                val taskStart = (t.startAt - kickOff).inWholeMinutes.toInt()
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

    // validations
    fun employeesWithOverlap(): List<String> =
        assignedTasks()
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
