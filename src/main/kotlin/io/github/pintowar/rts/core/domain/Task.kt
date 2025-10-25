package io.github.pintowar.rts.core.domain

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

sealed interface Task {
    val id: TaskId
    val description: String
    val priority: TaskPriority
    val requiredSkills: Map<String, SkillPoint>
    val dependsOn: Task?

    fun isAssigned(): Boolean = this is AssignedTask

    fun assign(employee: Employee, startAt: Instant, duration: Duration): Task =
        AssignedTask(id, description, priority, requiredSkills, dependsOn, employee, startAt, duration)

    fun unassign(): Task = UnassignedTask(id, description, priority, requiredSkills, dependsOn)

    fun endsAt(): Instant? {
        if (this is AssignedTask) return endsAt
        return null
    }

    fun overlaps(other: Task): Boolean {
        if (this is AssignedTask && other is AssignedTask) {
            return this.interval.overlaps(other.interval)
        }
        return false
    }
}

data class UnassignedTask(
    override val id: TaskId,
    override val description: String,
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
        ): Result<UnassignedTask> =
            valueOf(description, priority, skills, dependsOn).map { it.copy(id = TaskId(id)) }

        fun valueOf(
            description: String,
            priority: TaskPriority = TaskPriority.MINOR,
            skills: Map<String, SkillPoint> = emptyMap(),
            dependsOn: Task? = null
        ): Result<UnassignedTask> = runCatching {
            UnassignedTask(TaskId(), description, priority, skills, dependsOn)
        }
    }
}

data class AssignedTask(
    override val id: TaskId,
    override val description: String,
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