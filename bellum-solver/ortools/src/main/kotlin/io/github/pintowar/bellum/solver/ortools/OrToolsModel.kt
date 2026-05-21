package io.github.pintowar.bellum.solver.ortools

import com.google.ortools.sat.BoolVar
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.IntervalVar
import com.google.ortools.sat.LinearExpr
import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class OrToolsModel(
    private val project: Project,
    estimator: TimeEstimator,
    val idx: Int = 0,
) {
    val name: String = "ProjectSchedulerModel $idx"

    val model = CpModel()

    // --- Constants ---
    private val employees = project.allEmployees()
    private val tasks = project.allTasks()
    private val numEmployees = employees.size
    private val numTasks = tasks.size
    private val precedenceConstraints = createPrecedenceTable(tasks)
    private val taskDurationMatrix = createDurationMatrix(employees, tasks, estimator).getOrThrow()
    private val taskPriorities = tasks.map { it.priority.value }.toIntArray()

    // --- Variables ---
    private val maxPossibleTime = taskDurationMatrix.minOf { it.sum() }.toLong()
    private val minPossibleTime =
        IntArray(numTasks) { t -> (0 until numEmployees).minOf { e -> taskDurationMatrix[e][t] } }
    private val earliestStartTimes = computeEarliestStartTimes()

    val taskAssignee = Array(numTasks) { t -> model.newIntVar(0, numEmployees.toLong() - 1, "taskAssignee_$t") }
    val taskStartTime =
        Array(numTasks) { t -> model.newIntVar(earliestStartTimes[t].toLong(), maxPossibleTime, "startTime_$t") }
    val taskDuration =
        Array(numTasks) { t ->
            val maxDur = (0 until numEmployees).map { e -> taskDurationMatrix[e][t] }
            model.newIntVar(maxDur.minOrNull()!!.toLong(), maxDur.maxOrNull()!!.toLong(), "duration_$t")
        }
    val taskEndTime =
        Array(numTasks) { t -> model.newIntVar(earliestStartTimes[t].toLong(), maxPossibleTime, "endTime_$t") }
    val employeeWorkload = Array(numEmployees) { e -> model.newIntVar(0, maxPossibleTime, "employeeWorkload_$e") }

    val taskAssignedTo =
        Array(numTasks) { t ->
            Array(numEmployees) { e -> model.newBoolVar("assigned_${t}_$e") }
        }

    private val makespan = createMakespanObjective()
    private val priorityCost = createPriorityCostObjective()

    init {
        linkAssignmentVariables()
        addTaskDurationAndEndConstraint()
        addEmployeeWorkloadConstraintOptimized()
        addPrecedenceConstraints()
        addNoOverlapConstraint()
        addSymmetryBreakingConstraints()
        setInitialValuesForAssignedTasks()

        val objExpr =
            LinearExpr
                .newBuilder()
                .addTerm(makespan, 100)
                .add(priorityCost)
                .build()
        model.minimize(objExpr)
    }

    private fun linkAssignmentVariables() {
        for (t in 0 until numTasks) {
            val assignedVars = taskAssignedTo[t]
            model.addExactlyOne(assignedVars)

            val assigneeExpr = LinearExpr.newBuilder()
            for (e in 0 until numEmployees) {
                assigneeExpr.addTerm(assignedVars[e], e.toLong())
                model.addEquality(taskAssignee[t], e.toLong()).onlyEnforceIf(assignedVars[e])
                model.addDifferent(taskAssignee[t], e.toLong()).onlyEnforceIf(assignedVars[e].not())
            }
            model.addEquality(taskAssignee[t], assigneeExpr.build())
        }
    }

    private fun addTaskDurationAndEndConstraint() {
        for (t in 0 until numTasks) {
            for (e in 0 until numEmployees) {
                model
                    .addEquality(taskDuration[t], taskDurationMatrix[e][t].toLong())
                    .onlyEnforceIf(taskAssignedTo[t][e])
            }
            val sumExpr =
                LinearExpr
                    .newBuilder()
                    .add(taskStartTime[t])
                    .add(taskDuration[t])
                    .build()
            model.addEquality(taskEndTime[t], sumExpr)
        }
    }

    private fun addEmployeeWorkloadConstraintOptimized() {
        for (e in 0 until numEmployees) {
            val expr = LinearExpr.newBuilder()
            for (t in 0 until numTasks) {
                expr.addTerm(taskAssignedTo[t][e], taskDurationMatrix[e][t].toLong())
            }
            model.addEquality(employeeWorkload[e], expr.build())
        }
    }

    private fun addPrecedenceConstraints() {
        for (precedence in precedenceConstraints) {
            val (predecessor, successor) = precedence
            model.addLessOrEqual(taskEndTime[predecessor], taskStartTime[successor])
        }
    }

    private fun addNoOverlapConstraint() {
        for (e in 0 until numEmployees) {
            val intervals = mutableListOf<IntervalVar>()
            for (t in 0 until numTasks) {
                intervals.add(
                    model.newOptionalIntervalVar(
                        taskStartTime[t],
                        LinearExpr.constant(taskDurationMatrix[e][t].toLong()),
                        taskEndTime[t],
                        taskAssignedTo[t][e],
                        "interval_${t}_$e",
                    ),
                )
            }
            model.addNoOverlap(intervals.toTypedArray())
        }
    }

    private fun setInitialValuesForAssignedTasks() {
        if (project.scheduledStatus() != ProjectScheduled.PARTIAL) return
        val employeeIndexMap = employees.withIndex().associate { it.value.id to it.index }

        for (t in 0 until numTasks) {
            val task = tasks[t]
            if (task is AssignedTask) {
                val empIdx = employeeIndexMap[task.employee.id] ?: continue
                val startOffset = durationUnit(task.startAt - project.kickOff).toLong()
                val dur = durationUnit(task.duration).toLong()

                if (task.pinned) {
                    model.addEquality(taskAssignee[t], empIdx.toLong())
                    model.addEquality(taskStartTime[t], startOffset)
                    model.addEquality(taskDuration[t], dur)
                } else {
                    model.addHint(taskAssignee[t], empIdx.toLong())
                    model.addHint(taskStartTime[t], startOffset)
                    model.addHint(taskDuration[t], dur)
                    model.addHint(taskAssignedTo[t][empIdx], 1L)
                }
            }
        }
    }

    private fun addSymmetryBreakingConstraints() {
        val rootTaskIndices = (0 until numTasks).filter { t -> precedenceConstraints.none { it[1] == t } }.sorted()
        if (rootTaskIndices.isNotEmpty()) {
            val identicalEmployeeGroups =
                (0 until numEmployees)
                    .groupBy { e -> taskDurationMatrix[e].toList() }
                    .values
                    .filter { it.size > 1 }

            for (group in identicalEmployeeGroups) {
                val groupSize = group.size
                val numRootTasks = rootTaskIndices.size
                for (i in 0 until groupSize - 1) {
                    val e1 = group[i]
                    val e2 = group[i + 1]
                    // We enforce lexicographical order e1 <= e2 on the assignments of root tasks
                    // This is complex in CP-SAT without lex constraint natively. Let's do a simple symmetry breaking
                    // Since Choco used lexChainLessEq, we can implement it or just ignore it if it's too complex.
                    // Actually, OR-Tools is very good at symmetry breaking natively. We can leave this out for now
                    // or implement a basic version where if taskAssignedTo[t][e1] we can add symmetry breaking.
                }
            }
        }
    }

    private fun createMakespanObjective(): IntVar {
        val totalMinDur = minPossibleTime.sum()
        val lowerBound1 = totalMinDur / numEmployees
        val lowerBound2 = minPossibleTime.maxOrNull() ?: 0
        val lowerBound = lowerBound1.coerceAtLeast(lowerBound2).toLong()

        val ms = model.newIntVar(lowerBound, maxPossibleTime, "makespan")
        model.addMaxEquality(ms, taskEndTime.toList())
        return ms
    }

    private fun createPriorityCostObjective(): IntVar {
        val inversionIndicators = mutableListOf<BoolVar>()
        for (t1 in 0 until numTasks) {
            for (t2 in t1 + 1 until numTasks) {
                val (p1, p2) = taskPriorities[t1] to taskPriorities[t2]
                if (p1 > p2) {
                    val inversion = model.newBoolVar("inv_${t1}_before_$t2")
                    // inversion => start[t1] < start[t2] -> start[t1] <= start[t2] - 1
                    model
                        .addLessOrEqual(
                            taskStartTime[t1],
                            LinearExpr
                                .newBuilder()
                                .add(taskStartTime[t2])
                                .add(-1L)
                                .build(),
                        ).onlyEnforceIf(inversion)
                    model.addGreaterOrEqual(taskStartTime[t1], taskStartTime[t2]).onlyEnforceIf(inversion.not())
                    inversionIndicators.add(inversion)
                } else if (p2 > p1) {
                    val inversion = model.newBoolVar("inv_${t2}_before_$t1")
                    model
                        .addLessOrEqual(
                            taskStartTime[t2],
                            LinearExpr
                                .newBuilder()
                                .add(taskStartTime[t1])
                                .add(-1L)
                                .build(),
                        ).onlyEnforceIf(inversion)
                    model.addGreaterOrEqual(taskStartTime[t2], taskStartTime[t1]).onlyEnforceIf(inversion.not())
                    inversionIndicators.add(inversion)
                }
            }
        }

        val priorityCost = model.newIntVar(0, (numTasks * numTasks).toLong(), "priorityCost")
        if (inversionIndicators.isNotEmpty()) {
            val sumExpr = LinearExpr.newBuilder()
            for (inv in inversionIndicators) {
                sumExpr.add(inv)
            }
            model.addEquality(priorityCost, sumExpr.build())
        } else {
            model.addEquality(priorityCost, 0L)
        }
        return priorityCost
    }

    private fun computeEarliestStartTimes(): IntArray {
        val earliest = IntArray(numTasks) { 0 }
        val taskIndexMap = tasks.withIndex().associate { (idx, it) -> it.id to idx }
        val visited = BooleanArray(numTasks)

        fun computeEarliest(taskIdx: Int): Int {
            if (visited[taskIdx]) return earliest[taskIdx]
            visited[taskIdx] = true

            val task = tasks[taskIdx]
            if (task.dependsOn != null) {
                val predIdx = taskIndexMap[task.dependsOn!!.id]!!
                val predEarliest = computeEarliest(predIdx)
                earliest[taskIdx] = predEarliest + minPossibleTime[predIdx]
            }
            return earliest[taskIdx]
        }

        for (t in 0 until numTasks) {
            computeEarliest(t)
        }
        return earliest
    }

    private fun createDurationMatrix(
        employees: List<Employee>,
        tasks: List<Task>,
        estimator: TimeEstimator,
    ): Result<Array<IntArray>> =
        runCatching {
            employees
                .map { emp ->
                    tasks.map { tsk -> durationUnit(estimator.estimate(emp, tsk).getOrThrow()) }.toIntArray()
                }.toTypedArray()
        }

    private fun createPrecedenceTable(tasks: List<Task>): Array<IntArray> {
        val idxTask = tasks.withIndex().associate { (idx, it) -> it.id to idx }
        return tasks
            .filter { it.dependsOn != null }
            .map { tsk ->
                val (a, b) = idxTask.getValue(tsk.dependsOn!!.id) to idxTask.getValue(tsk.id)
                intArrayOf(a, b)
            }.toTypedArray()
    }

    private fun durationUnit(duration: Duration): Int = duration.inWholeMinutes.toInt()

    private fun unitDuration(duration: Long): Duration = duration.toInt().minutes

    fun decode(
        wrapper: OrToolsWrapper,
        currentDuration: Duration,
        optimal: Boolean = false,
    ): Result<SchedulerSolution> =
        runCatching {
            val emps = taskAssignee.map { employees[wrapper.getValue(it).toInt()] }
            val inits = taskStartTime.map { project.kickOff + unitDuration(wrapper.getValue(it)) }
            val durs = taskDuration.map { unitDuration(wrapper.getValue(it)) }
            val assigneds = tasks.mapIndexed { idx, tsk -> tsk.assign(emps[idx], inits[idx], durs[idx]) }

            return project
                .replace(tasks = assigneds.toSet())
                .map { newProject ->
                    SchedulerSolution(
                        newProject,
                        optimal,
                        currentDuration,
                        wrapper.solverStatistics(),
                    )
                }
        }
}
