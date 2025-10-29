package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ChocoScheduler(
    override val estimator: TimeEstimator,
    private val withLexicalConstraint: Boolean = true,
) : Scheduler() {
    override fun solve(
        project: Project,
        timeLimit: Duration,
        startTime: Instant,
    ): Result<SchedulerSolution> =
        runCatching {
            val employees = project.allEmployees()
            val tasks = project.allTasks()

            val numEmployees = employees.size
            val numTasks = tasks.size
            val precedences = precedenceTable(tasks)
            val matrix = durationMatrix(employees, tasks).getOrThrow()
            val taskPriorities = tasks.map { it.priority.value }.toIntArray()

            val modelVars = generateModel(numEmployees, numTasks, matrix, precedences, taskPriorities, withLexicalConstraint)
            val solver = modelVars.model.solver

            solver.limitTime(timeLimit.inWholeMilliseconds)
            val solution = Solution(modelVars.model)
            val initSolving = Clock.System.now()
            while (solver.solve()) {
                solution.record()
                val (currentDuration, optimal) = (Clock.System.now() - initSolving) to solver.isSearchCompleted
                decode(startTime, employees, tasks, solution, modelVars, currentDuration, optimal).onSuccess {
                    listeners.forEach { listener -> listener(it) }
                }
            }
            val currentDuration = Clock.System.now() - initSolving
            return decode(startTime, employees, tasks, solution, modelVars, currentDuration, solver.isObjectiveOptimal)
        }

    private fun decode(
        startTime: Instant,
        employees: List<Employee>,
        tasks: List<Task>,
        solution: Solution,
        modelVars: ModelVars,
        currentDuration: Duration,
        optimal: Boolean = false,
    ): Result<SchedulerSolution> =
        runCatching {
            val emps = modelVars.taskAssignee.map { employees[solution.getIntVal(it)] }
            val inits = modelVars.taskStartTime.map { startTime + unitDuration(solution.getIntVal(it)) }
            val durs = modelVars.taskDuration.map { unitDuration(solution.getIntVal(it)) }
            val assigneds = tasks.mapIndexed { idx, tsk -> tsk.assign(emps[idx], inits[idx], durs[idx]) }

            Project(employees.toSet(), assigneds.toSet())
                .map { newProject ->
                    SchedulerSolution(
                        newProject,
                        optimal,
                        currentDuration,
                    )
                }.getOrThrow()
        }

    private fun generateModel(
        numEmployees: Int,
        numTasks: Int,
        matrix: Array<IntArray>,
        precedences: Array<IntArray>,
        taskPriorities: IntArray,
        withLexicalConstraint: Boolean = true,
    ): ModelVars {
        val model = Model("ProjectSchedulerModel")
        // --- Variables ---

        // taskAssignee[t] = e => task 't' is assigned to employee 'e'
        val taskAssignee = model.intVarArray("taskAssignee", numTasks, 0, numEmployees - 1)

        // Calculate a safe upper bound for time-related variables
        val maxPossibleTime = matrix.sumOf { it.sum() }
        val maxPossibleDuration = matrix.maxOf { it.max() }

        // employeeWorkload[e] = total time for employee 'e'
        val employeeWorkload = model.intVarArray("employeeWorkload", numEmployees, 0, maxPossibleTime)

        // Variables for handling precedence constraints
        val taskStartTime = model.intVarArray("startTime", numTasks, 0, maxPossibleTime)
        val taskDuration = model.intVarArray("duration", numTasks, 0, maxPossibleDuration)
        val taskEndTime = Array(numTasks) { i -> taskStartTime[i].add(taskDuration[i]).intVar() }

        // makespan = max(employeeWorkload) -> the variable to minimize
        val makespan = model.intVar("makespan", 0, maxPossibleTime)

        // --- Constraints ---

        if (withLexicalConstraint) {
            // Add a lexicographic constraint for tasks without dependencies to break symmetries
            // This should only be applied to groups of identical employees.
            val rootTaskIndices =
                (0 until numTasks)
                    .filter { t -> precedences.none { it[1] == t } }
                    .sorted()

            if (rootTaskIndices.isNotEmpty()) {
                val identicalEmployeeGroups =
                    (0 until numEmployees)
                        .groupBy { e -> matrix[e].toList() }
                        .values
                        .filter { it.size > 1 }

                for (group in identicalEmployeeGroups) {
                    val groupSize = group.size
                    val numRootTasks = rootTaskIndices.size
                    val employeeToRootTasks =
                        Array(groupSize) { i ->
                            val e = group[i]
                            Array(numRootTasks) { tIdx ->
                                val t = rootTaskIndices[tIdx]
                                model.arithm(taskAssignee[t], "=", e).reify()
                            }
                        }
                    model.lexChainLessEq(*employeeToRootTasks).post()
                }
            }
        }

        // Constraint: Link task duration to the assigned employee's estimated time
        for (t in 0 until numTasks) {
            // Get the estimated times for task 't' for all employees
            val taskTimes = IntArray(numEmployees) { e -> matrix[e][t] }
            // taskDuration[t] = taskTimes[taskAssignee[t]]
            model.element(taskDuration[t], taskTimes, taskAssignee[t]).post()
        }

        // Constraint: Calculate the workload for each employee
        // This is a robust way to model: employeeWorkload[e] = sum(duration[t] for all t assigned to e)
        for (e in 0 until numEmployees) {
            // Create an array of variables representing the duration of each task IF assigned to this employee
            val assignedTaskDurations =
                Array(numTasks) { t ->
                    model.intVar("duration_e${e}_t$t", 0, matrix[e][t])
                }

            for (t in 0 until numTasks) {
                // If task 't' is assigned to employee 'e', then its duration is estimationMatrix[e][t], else 0.
                model.ifThenElse(
                    model.arithm(taskAssignee[t], "=", e),
                    model.arithm(assignedTaskDurations[t], "=", matrix[e][t]),
                    model.arithm(assignedTaskDurations[t], "=", 0),
                )
            }
            // The employee's total workload is the sum of these conditional durations
            model.sum(assignedTaskDurations, "=", employeeWorkload[e]).post()
        }

        // Constraint: Respect precedences
        for (precedence in precedences) {
            val predecessor = precedence[0]
            val successor = precedence[1]
            // The predecessor task must end before the successor task can start
            model.arithm(taskEndTime[predecessor], "<=", taskStartTime[successor]).post()
        }

        // Constraint: No two tasks assigned to the same employee can overlap in time.
        // We model each task as a rectangle in a 2D plane (time vs. employee).
        // The diffn constraint ensures none of these rectangles overlap.
        val taskHeights = Array(numTasks) { model.intVar(1) } // Each task uses 1 employee "unit"
        model.diffN(taskStartTime, taskAssignee, taskDuration, taskHeights, false).post()

        // Constraint: The makespan is the maximum of all employee workloads
        // model.max(makespan, employeeWorkload).post()
        model.max(makespan, taskEndTime).post()

        // A list to hold indicator variables for each potential priority inversion
        val inversionIndicators = mutableListOf<IntVar>()

        // Iterate over all unique pairs of tasks
        for (t1 in 0 until numTasks) {
            for (t2 in t1 + 1 until numTasks) {
                val p1 = taskPriorities[t1]
                val p2 = taskPriorities[t2]

                // Case 1: Task 1 has lower priority than Task 2.
                // Penalize if Task 1 starts before Task 2.
                if (p1 > p2) {
                    val inversion = model.boolVar("inv_${t1}_before_$t2")
                    // inversion = 1 if startTime[t1] < startTime[t2], 0 otherwise
                    model.arithm(taskStartTime[t1], "<", taskStartTime[t2]).reifyWith(inversion)
                    inversionIndicators.add(inversion)
                } else if (p2 > p1) {
                    // Case 2: Task 2 has lower priority than Task 1.
                    // Penalize if Task 2 starts before Task 1.
                    val inversion = model.boolVar("inv_${t2}_before_$t1")
                    // inversion = 1 if startTime[t2] < startTime[t1], 0 otherwise
                    model.arithm(taskStartTime[t2], "<", taskStartTime[t1]).reifyWith(inversion)
                    inversionIndicators.add(inversion)
                }
            }
        }

        // The total cost is the sum of all priority inversions.
        // The lower this value, the better the priority ordering.
        val priorityCost = model.intVar("priorityCost", 0, numTasks * numTasks)
        if (inversionIndicators.isNotEmpty()) {
            model.sum(inversionIndicators.toTypedArray(), "=", priorityCost).post()
        } else {
            // If there are no pairs with different priorities, cost is always 0.
            model.arithm(priorityCost, "=", 0).post()
        }

        // Objective
//        model.setObjective(Model.MINIMIZE, makespan)
        model.setObjective(Model.MINIMIZE, makespan.mul(100).add(priorityCost).intVar())

        return ModelVars(
            model = model,
            priorityCost = priorityCost,
            makespan = makespan,
            taskAssignee = taskAssignee,
            taskStartTime = taskStartTime,
            taskDuration = taskDuration,
        )
    }

    private fun durationMatrix(
        employees: List<Employee>,
        tasks: List<Task>,
    ): Result<Array<IntArray>> =
        runCatching {
            employees
                .map { emp ->
                    tasks.map { tsk -> durationUnit(estimator.estimate(emp, tsk).getOrThrow()) }.toIntArray()
                }.toTypedArray()
        }

    private fun precedenceTable(tasks: List<Task>): Array<IntArray> {
        val idxTask = tasks.withIndex().associate { (idx, it) -> it.id to idx }
        return tasks
            .filter { it.dependsOn != null }
            .map { tsk ->
                val (a, b) = idxTask.getValue(tsk.dependsOn!!.id) to idxTask.getValue(tsk.id)
                intArrayOf(a, b)
            }.toTypedArray()
    }

    fun durationUnit(duration: Duration): Int = duration.inWholeMinutes.toInt()

    fun unitDuration(duration: Int): Duration = duration.minutes

    data class ModelVars(
        val model: Model,
        val priorityCost: IntVar,
        val makespan: IntVar,
        val taskAssignee: Array<IntVar>,
        val taskStartTime: Array<IntVar>,
        val taskDuration: Array<IntVar>,
    )
}
