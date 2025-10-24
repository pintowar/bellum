package io.github.pintowar.rts.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import arrow.core.raise.mapOrAccumulate
import io.github.pintowar.rts.core.util.Helper
import java.util.UUID

interface InvalidProject
class OverlapingTasks(val employee: Employee): InvalidProject

enum class ProjectScheduled { NONE, PARTIAL, SCHEDULED }

@JvmInline value class ProjectId(private val id: UUID)  {
    constructor(): this(Helper.uuidV7())

    operator fun invoke() = id
}

data class Project(
    val id: ProjectId,
    private val employees: Set<Employee>,
    private val tasks: Set<Task>,
) {

    companion object {
        operator fun invoke(id: UUID, employees: Set<Employee>, tasks: Set<Task>): Project =
            invoke(employees, tasks).copy(id = ProjectId(id))

        operator fun invoke(employees: Set<Employee>, tasks: Set<Task>): Project =
            Project(ProjectId(), employees, tasks)
    }

    fun employees() = employees.toList()

    fun tasks() = tasks.toList()

    fun scheduledStatus(): ProjectScheduled {
        val numAssignedTask = tasks.count { it.isAssigned() }
        return when (numAssignedTask) {
            0 -> ProjectScheduled.NONE
            tasks.size -> ProjectScheduled.SCHEDULED
            else -> ProjectScheduled.PARTIAL
        }
    }

    fun validateOverlapingTasks(): Either<OverlapingTasks, Unit> = either {
        val res = tasks.asSequence()
            .filter { it.isAssigned() }
            .map { it as AssignedTask }
            .groupBy({ it.employee })
            .mapValues { (_, tasks) -> tasks.hasOverlappingIntervals() }

        val errors = res.mapNotNull { (emp, hasOverlap) ->  OverlapingTasks(emp).takeIf { hasOverlap }}

    }

//    fun precedencesTable() {
//        val zaz = tasks
//            .filter { it.dependsOn != null }
//            .map { it.dependsOn?.id!! to it.id }
//    }

}
