package io.github.pintowar.rts.core.domain

import arrow.core.Either
import arrow.core.raise.either
import io.github.pintowar.rts.core.util.Helper
import org.threeten.extra.Interval
import java.time.Duration
import java.time.Instant
import java.util.UUID

interface InvalidTask
object InvalidTaskName: InvalidTask

enum class TaskPriority { CRITICAL, MAJOR, MINOR }

@JvmInline value class TaskId(private val id: UUID)  {
    constructor(): this(Helper.uuidV7())

    operator fun invoke() = id
}

@JvmInline value class TaskDescription(private val description: String)  {
    companion object {
        fun valueOf(description: String): Either<InvalidTaskName, TaskDescription> = either {
            if (description.isBlank()) raise(InvalidTaskName)
            TaskDescription(description)
        }
    }

    operator fun invoke() = description
}

sealed interface Task {
    val id: TaskId
    val description: TaskDescription
    val priority: TaskPriority
    val requiredSkills: Map<String, SkillPoint>
    val dependsOn: Task?

    fun isAssigned(): Boolean = this is AssignedTask

    fun assign(employee: Employee, startAt: Instant, duration: Duration): Task =
        AssignedTask(id, description, priority, requiredSkills, dependsOn, employee, startAt, duration)

    fun unassign(): Task = UnassignedTask(id, description, priority, requiredSkills, dependsOn)

    fun overlaps(other: Task): Boolean {
        if (this is AssignedTask && other is AssignedTask) {
            return this.interval.overlaps(other.interval)
        }
        return false
    }
}

data class UnassignedTask(
    override val id: TaskId,
    override val description: TaskDescription,
    override val priority: TaskPriority,
    override val requiredSkills: Map<String, SkillPoint>,
    override val dependsOn: Task? = null,
) : Task {

    companion object {
        fun valueOf(
            id: UUID,
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null
        ): Either<InvalidTask, UnassignedTask> = either {
            valueOf(description, priority, skills, dependsOn).bind().copy(id = TaskId(id))
        }

        fun valueOf(
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null
        ): Either<InvalidTask, UnassignedTask> = either {
            val employeeName = TaskDescription.valueOf(description).bind()
            UnassignedTask(TaskId(), employeeName, priority, skills, dependsOn)
        }
    }
}

data class AssignedTask(
    override val id: TaskId,
    override val description: TaskDescription,
    override val priority: TaskPriority,
    override val requiredSkills: Map<String, SkillPoint>,
    override val dependsOn: Task? = null,
    val employee: Employee,
    val startAt: Instant,
    val duration: Duration,
) : Task {

    val endsAt: Instant = startAt + duration

    val interval = Interval.of(startAt, duration)

}