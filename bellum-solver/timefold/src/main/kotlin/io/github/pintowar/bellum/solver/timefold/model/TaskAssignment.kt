package io.github.pintowar.bellum.solver.timefold.model

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.TaskPriority
import java.util.UUID

@PlanningEntity
class TaskAssignment {
    @PlanningId
    var taskId: UUID? = null
    lateinit var taskDescription: String
    lateinit var taskPriority: TaskPriority
    var dependsOnTaskId: UUID? = null
    var requiredDuration: Int = 0

    @PlanningVariable
    var employee: Employee? = null

    @PlanningVariable
    var startTimeMinute: Int? = null

    var pinned: Boolean = false

    var pinnedEmployeeId: String? = null
    var pinnedStartTimeMinute: Int? = null

    @Transient
    var durationMap: Map<String, Int> = emptyMap()

    constructor()

    constructor(
        taskId: UUID,
        taskDescription: String,
        taskPriority: TaskPriority,
        dependsOnTaskId: UUID?,
        requiredDuration: Int,
        pinned: Boolean = false,
        pinnedEmployeeId: String? = null,
        pinnedStartTimeMinute: Int? = null,
    ) {
        this.taskId = taskId
        this.taskDescription = taskDescription
        this.taskPriority = taskPriority
        this.dependsOnTaskId = dependsOnTaskId
        this.requiredDuration = requiredDuration
        this.pinned = pinned
        this.pinnedEmployeeId = pinnedEmployeeId
        this.pinnedStartTimeMinute = pinnedStartTimeMinute
    }

    fun getDuration(): Int {
        val employee = this.employee ?: return requiredDuration
        val employeeId = employee.id().toString()
        val taskIdStr = taskId?.toString() ?: return requiredDuration
        return durationMap["$employeeId|$taskIdStr"] ?: requiredDuration
    }
}
