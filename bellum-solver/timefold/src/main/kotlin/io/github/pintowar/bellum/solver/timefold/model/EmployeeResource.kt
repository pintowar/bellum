package io.github.pintowar.bellum.solver.timefold.model

import ai.timefold.solver.core.api.domain.lookup.PlanningId
import java.util.UUID

// @PlanningEntity
class EmployeeResource {
    @PlanningId
    var employeeId: UUID? = null

    lateinit var name: String

    constructor()

    constructor(employeeId: UUID?, name: String) {
        this.employeeId = employeeId
        this.name = name
    }
}
