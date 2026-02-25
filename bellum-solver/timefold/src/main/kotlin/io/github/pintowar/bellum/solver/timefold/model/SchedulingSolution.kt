package io.github.pintowar.bellum.solver.timefold.model

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import io.github.pintowar.bellum.core.domain.Employee

@PlanningSolution
class SchedulingSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    lateinit var employees: List<Employee>

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    lateinit var taskRange: List<Int>

    @PlanningEntityCollectionProperty
    lateinit var taskAssignments: List<TaskAssignment>

    @PlanningScore
    var score: HardSoftScore? = null

    lateinit var durationMap: Map<String, Int>

    var maxPossibleTime: Int = 0

    var minPossibleTime: Map<Int, Int> = emptyMap()

    var earliestStartTimes: Map<Int, Int> = emptyMap()

    var kickOffTime: Long = 0

    constructor()

    constructor(
        employees: List<Employee>,
        taskRange: List<Int>,
        taskAssignments: List<TaskAssignment>,
        durationMap: Map<String, Int>,
        maxPossibleTime: Int,
        minPossibleTime: Map<Int, Int>,
        earliestStartTimes: Map<Int, Int>,
        kickOffTime: Long,
    ) {
        this.employees = employees
        this.taskRange = taskRange
        this.taskAssignments = taskAssignments
        this.durationMap = durationMap
        this.maxPossibleTime = maxPossibleTime
        this.minPossibleTime = minPossibleTime
        this.earliestStartTimes = earliestStartTimes
        this.kickOffTime = kickOffTime
    }
}
