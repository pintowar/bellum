package io.github.pintowar.bellum.solver.timefold.model

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable
import java.util.UUID

@PlanningEntity
class EmployeeResource {
    @PlanningId
    var employeeId: UUID? = null

    lateinit var name: String

    @PlanningListVariable(allowsUnassignedValues = false)
    var tasks: MutableList<TaskAssignment> = mutableListOf()

    constructor()

    constructor(employeeId: UUID?, name: String, tasks: MutableList<TaskAssignment> = mutableListOf()) {
        this.employeeId = employeeId
        this.name = name
        this.tasks = tasks
    }
}
