package io.github.pintowar.bellum.core.domain

import io.konform.validation.Validation
import io.konform.validation.constraints.notBlank
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant

enum class TaskPriority(
    val value: Int,
) {
    CRITICAL(0),
    MAJOR(1),
    MINOR(2),
}

sealed interface Task {
    val id: TaskId
    val description: String
    val priority: TaskPriority
    val requiredSkills: Map<String, SkillPoint>
    val dependsOn: Task?

    fun changeDependency(dependsOn: Task? = null): Task

    fun isAssigned(): Boolean = this is AssignedTask

    fun assign(
        employee: Employee,
        startAt: Instant,
        duration: Duration,
    ): Task = AssignedTask(id(), description, priority, requiredSkills, dependsOn, employee, startAt, duration).getOrThrow()

    fun unassign(): Task = UnassignedTask(id(), description, priority, requiredSkills, dependsOn).getOrThrow()

    fun endsAt(): Instant? {
        if (this is AssignedTask) return endsAt
        return null
    }

    fun overlaps(other: Task): Boolean {
        if (this is AssignedTask && other is AssignedTask) {
            return this == other || startAt < other.endsAt && other.startAt < endsAt
        }
        return false
    }
}

class UnassignedTask private constructor(
    override val id: TaskId,
    override val description: String,
    override val priority: TaskPriority,
    override val requiredSkills: Map<String, SkillPoint>,
    override val dependsOn: Task? = null,
) : Task {
    companion object {
        private val validator =
            Validation {
                Task::description {
                    notBlank()
                }
            }

        operator fun invoke(
            id: UUID,
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null,
        ): Result<UnassignedTask> =
            UnassignedTask(
                TaskId(id),
                description,
                priority,
                skills,
                dependsOn,
            ).validateAndWrap(validator)

        operator fun invoke(
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null,
        ): Result<UnassignedTask> = invoke(TaskId()(), description, priority, skills, dependsOn)
    }

    override fun changeDependency(dependsOn: Task?): UnassignedTask =
        invoke(id(), description, priority, requiredSkills, dependsOn).getOrThrow()
}

class AssignedTask private constructor(
    override val id: TaskId,
    override val description: String,
    override val priority: TaskPriority,
    override val requiredSkills: Map<String, SkillPoint>,
    override val dependsOn: Task? = null,
    val employee: Employee,
    val startAt: Instant,
    val duration: Duration,
    val pinned: Boolean = false,
) : Task {
    companion object {
        private val validator =
            Validation {
                Task::description {
                    notBlank()
                }
            }

        operator fun invoke(
            id: UUID,
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null,
            employee: Employee,
            startAt: Instant,
            duration: Duration,
            pinned: Boolean = false,
        ): Result<AssignedTask> =
            AssignedTask(
                TaskId(id),
                description,
                priority,
                skills,
                dependsOn,
                employee,
                startAt,
                duration,
                pinned,
            ).validateAndWrap(validator)

        operator fun invoke(
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null,
            employee: Employee,
            startAt: Instant,
            duration: Duration,
            pinned: Boolean = false,
        ): Result<AssignedTask> = invoke(TaskId()(), description, priority, skills, dependsOn, employee, startAt, duration, pinned)
    }

    val endsAt: Instant = startAt + duration

    fun pin(): AssignedTask =
        if (pinned) this else invoke(id(), description, priority, requiredSkills, dependsOn, employee, startAt, duration, true).getOrThrow()

    override fun changeDependency(dependsOn: Task?): AssignedTask =
        invoke(id(), description, priority, requiredSkills, dependsOn, employee, startAt, duration, pinned).getOrThrow()
}
