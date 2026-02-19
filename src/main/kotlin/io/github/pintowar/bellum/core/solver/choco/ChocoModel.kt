package io.github.pintowar.bellum.core.solver.choco

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import org.chocosolver.solver.Model
import org.chocosolver.solver.ResolutionPolicy
import org.chocosolver.solver.Solution
import org.chocosolver.solver.Solver
import org.chocosolver.solver.search.SearchState
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail
import org.chocosolver.solver.search.strategy.selectors.variables.Smallest
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * This class encapsulates the logic for building a Constraint Programming (CP) model
 * to solve the project scheduling problem using the Choco Solver.
 *
 * The model aims to assign tasks to employees and schedule them over time,
 * respecting various constraints and optimizing for certain objectives.
 *
 * @param project The project containing the tasks and employees to be scheduled.
 * @param estimator A [TimeEstimator] used to determine the duration of a task for a given employee.
 */
internal class ChocoModel(
    private val project: Project,
    estimator: TimeEstimator,
    idx: Int = 0,
) {
    /**
     * The main Choco Solver model instance.
     */
    internal val model = Model("ProjectSchedulerModel $idx")

    // --- Constants ---

    /**
     * A list of all employees available for the project.
     */
    private val employees = project.allEmployees()

    /**
     * A list of all tasks that need to be scheduled.
     */
    private val tasks = project.allTasks()

    /**
     * The total number of employees.
     */
    private val numEmployees = employees.size

    /**
     * The total number of tasks.
     */
    private val numTasks = tasks.size

    /**
     * A table representing task dependencies. Each entry is an array `[predecessorId, successorId]`,
     * indicating that the predecessor task must be completed before the successor task can begin.
     */
    private val precedenceConstraints = createPrecedenceTable(tasks)

    /**
     * A 2D matrix where `taskDurationMatrix[e][t]` holds the estimated time (in minutes)
     * for employee `e` to complete task `t`.
     */
    private val taskDurationMatrix = createDurationMatrix(employees, tasks, estimator).getOrThrow()

    /**
     * An array containing the priority value for each task.
     */
    private val taskPriorities = tasks.map { it.priority.value }.toIntArray()

    // --- Variables ---

    /**
     * An array of decision variables where `taskAssignee[t]` represents the index of the employee (`e`)
     * assigned to task `t`. The domain of each variable is `[0, numEmployees - 1]`.
     */
    private val taskAssignee = model.intVarArray("taskAssignee", numTasks, 0, numEmployees - 1)

    /**
     * A safe upper bound for time-related variables, calculated as the min of max duration for all employees
     * (as if all tasks were assigned to only that employee).
     * This ensures that variables like `taskStartTime` have a sufficiently large domain.
     */
    private val maxPossibleTime = taskDurationMatrix.minOf { it.sum() }

    /**
     * An array containing the minimum possible duration for each task.
     * For each task `t`, this is the shortest duration achievable across all employees.
     * Used to calculate a tighter lower bound for the makespan objective.
     */
    private val minPossibleTime = IntArray(numTasks) { t -> (0 until numEmployees).minOf { e -> taskDurationMatrix[e][t] } }

    /**
     * Earliest possible start time for each task based on precedence constraints.
     * Computed using minimum task durations as optimistic estimates.
     */
    private val earliestStartTimes = computeEarliestStartTimes()

    /**
     * An array of variables where `employeeWorkload[e]` represents the total time (in minutes)
     * that employee `e` is busy with assigned tasks.
     */
    private val employeeWorkload = model.intVarArray("employeeWorkload", numEmployees, 0, maxPossibleTime)

    /**
     * An array of variables where `taskStartTime[t]` represents the start time (in minutes, relative to project kick-off)
     * of task `t`. Domain is tightened using precedence analysis.
     */
    private val taskStartTime =
        Array(numTasks) { t ->
            model.intVar("startTime_$t", earliestStartTimes[t], maxPossibleTime)
        }

    /**
     * An array of variables where `taskDuration[t]` represents the duration (in minutes) of task `t`.
     * This value depends on which employee is assigned to the task.
     */
    private val taskDuration =
        Array(numTasks) { t ->
            val maxDur = (0 until numEmployees).map { e -> taskDurationMatrix[e][t] }.toIntArray()
            model.intVar("duration_$t", maxDur)
        }

    /**
     * An array of variables representing the end time of each task, calculated as `taskStartTime[t] + taskDuration[t]`.
     */
    private val taskEndTime = Array(numTasks) { i -> taskStartTime[i].add(taskDuration[i]).intVar() }

    /**
     * The primary objective variable: the makespan of the project.
     * This is the time when the last task is completed, which we aim to minimize.
     */
    private val makespan = createMakespanObjective()

    /**
     * The secondary objective variable: a cost associated with priority inversions.
     * An inversion occurs when a lower-priority task starts before a higher-priority task.
     * Minimizing this helps ensure that important tasks are scheduled earlier.
     */
    private val priorityCost = createPriorityCostObjective()

    /**
     * Boolean variables indicating if task t is assigned to employee e.
     * Lazily created when the optimized workload constraint is needed.
     */
    private val taskAssignedTo: Array<Array<BoolVar>> by lazy {
        Array(numTasks) { t ->
            Array(numEmployees) { e ->
                model.boolVar("assigned_${t}_$e")
            }
        }
    }

    init {
        // Set initial values for already assigned tasks
        setInitialValuesForAssignedTasks()

        // Link assignment variables to boolean matrix (lazy)
        linkAssignmentVariables()

        // Add constraints to the model
        addTaskDurationConstraint()
        addEmployeeWorkloadConstraintOptimized()
        addPrecedenceConstraints()
        addNoOverlapConstraint()
        addSymmetryBreakingConstraints()

        // Set the multi-objective function to minimize.
        // The makespan is given a much higher weight (100) to prioritize it over the priority cost.
        model.setObjective(Model.MINIMIZE, makespan.mul(100).add(priorityCost).intVar())

        configureSearchStrategy()
    }

    /**
     * Configures and returns the solver with a specified time limit.
     *
     * @param timeLimit The maximum time allowed for the solver to find a solution.
     * @return A configured [Solver] instance.
     */
    fun solver(timeLimit: Duration): Solver =
        model.solver.also {
            it.limitTime(timeLimit.inWholeMilliseconds)
        }

    /**
     * Creates a [Solution] object to store the results found by the solver.
     */
    fun solution() = Solution(model)

    /**
     * Decodes a solver [Solution] into a business-friendly [SchedulerSolution].
     * This involves mapping solver variables back to domain objects like tasks and employees.
     *
     * @param solution The solution found by the Choco solver.
     * @param currentDuration The time taken by the solver to find this solution.
     * @param optimal A flag indicating whether the solution is proven to be optimal.
     * @return A [Result] containing the decoded [SchedulerSolution] or an error if decoding fails.
     */
    fun decode(
        solution: Solution,
        currentDuration: Duration,
        optimal: Boolean = false,
    ): Result<SchedulerSolution> =
        runCatching {
            val emps = taskAssignee.map { employees[solution.getIntVal(it)] }
            val inits = taskStartTime.map { project.kickOff + unitDuration(solution.getIntVal(it)) }
            val durs = taskDuration.map { unitDuration(solution.getIntVal(it)) }
            val assigneds = tasks.mapIndexed { idx, tsk -> tsk.assign(emps[idx], inits[idx], durs[idx]) }

            return project
                .replace(tasks = assigneds.toSet())
                .map { newProject ->
                    SchedulerSolution(
                        newProject,
                        optimal,
                        currentDuration,
                        solverStatistics(model.solver),
                    )
                }
        }

    /**
     * Extracts and formats solver statistics from a Choco Solver instance.
     *
     * This function collects various performance metrics and runtime information from the solver,
     * formatting them into a human-readable map. The statistics include information about the
     * search process, solution quality, and solver configuration.
     *
     * @param solver The Choco Solver instance to extract statistics from.
     * @return A map containing solver information and statistics with the following keys:
     *         - "solver": The solver name ("Choco Solver")
     *         - "model name": The name of the constraint programming model
     *         - "search state": Current state of the search (e.g., NEW, RUNNING, TERMINATED, STOPPED, KILLED)
     *         - "solutions": Number of solutions found
     *         - "build time": Time taken to build the model
     *         - "resolution time": Total time spent in resolution
     *         - "policy": Resolution policy used (e.g., SATISFACTION, MINIMIZE, MAXIMIZE)
     *         - "objective": Objective value
     *         - "nodes": Number of nodes explored in the search tree
     *         - "backtracks": Number of backtracks during search
     *         - "fails": Number of failures encountered
     *         - "restarts": Number of restarts performed
     */
    fun solverStatistics(solver: Solver): Map<String, Any> {
        val columns =
            listOf(
                "search state",
                "solutions",
                "build time",
                "resolution time",
                "policy",
                "objective",
                "nodes",
                "backtracks",
                "fails",
                "restarts",
            )
        val stats =
            columns.zip(solver.toArray()).toMap().mapValues { (k, v) ->
                when (k) {
                    "search state" -> SearchState.entries[v.toInt()].toString()
                    "policy" -> ResolutionPolicy.entries[v.toInt()].toString()
                    else -> v
                }
            }
        return mapOf("solver" to "Choco Solver", "model name" to solver.modelName) + stats
    }

    /**
     * Adds symmetry breaking constraints to improve solver performance.
     *
     * When multiple employees have identical task duration profiles, assigning root tasks
     * (tasks with no predecessors) to any of them produces equivalent solutions. This method
     * breaks these symmetries by enforcing a lexicographic ordering on root task assignments
     * within groups of identical employees, reducing the search space without affecting solution quality.
     */
    private fun addSymmetryBreakingConstraints() {
        val rootTaskIndices =
            (0 until numTasks)
                .filter { t -> precedenceConstraints.none { it[1] == t } }
                .sorted()

        if (rootTaskIndices.isNotEmpty()) {
            val identicalEmployeeGroups =
                (0 until numEmployees)
                    .groupBy { e -> taskDurationMatrix[e].toList() }
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

    /**
     * Adds a constraint to link the duration of each task to the estimated time of the employee assigned to it.
     * It uses an `element` constraint, which is equivalent to:
     * `taskDuration[t] = taskDurationMatrix[taskAssignee[t]][t]`
     */
    private fun addTaskDurationConstraint() {
        for (t in 0 until numTasks) {
            // Get the estimated times for task 't' for all employees
            val taskTimes = IntArray(numEmployees) { e -> taskDurationMatrix[e][t] }
            // taskDuration[t] = taskTimes[taskAssignee[t]]
            model.element(taskDuration[t], taskTimes, taskAssignee[t]).post()
        }
    }

    /**
     * Optimized workload constraint using scalar instead of O(E × T) ifThenElse constraints.
     * Uses pre-computed boolean assignment variables for efficiency.
     * employeeWorkload[e] = sum(taskDuration[t] * taskAssignedTo[t][e] for all t)
     */
    private fun addEmployeeWorkloadConstraintOptimized() {
        for (e in 0 until numEmployees) {
            val coefficients = IntArray(numTasks) { t -> taskDurationMatrix[e][t] }
            val boolsAsInts = taskAssignedTo.map { it[e] }.toTypedArray()
            model.scalar(boolsAsInts, coefficients, "=", employeeWorkload[e]).post()
        }
    }

    /**
     * Links the taskAssignee variable to the boolean assignment matrix.
     * taskAssignedTo[t][e] = 1 iff taskAssignee[t] == e
     */
    private fun linkAssignmentVariables() {
        for (t in 0 until numTasks) {
            for (e in 0 until numEmployees) {
                model.arithm(taskAssignee[t], "=", e).reifyWith(taskAssignedTo[t][e])
            }
        }
    }

    /**
     * Adds constraints to enforce task precedences.
     * For each pair of tasks (A, B) where A is a predecessor of B, it adds the constraint:
     * `taskEndTime[A] <= taskStartTime[B]`
     */
    private fun addPrecedenceConstraints() {
        for (precedence in precedenceConstraints) {
            val (predecessor, successor) = precedence
            // The predecessor task must end before the successor task can start
            model.arithm(taskEndTime[predecessor], "<=", taskStartTime[successor]).post()
        }
    }

    /**
     * Adds a global `diffN` constraint to ensure that no two tasks assigned to the same employee overlap in time.
     *
     * This can be visualized as scheduling rectangles in a 2D plane, where one axis is time and the other is
     * employees. Each task is a rectangle with a width equal to its duration and a height of 1.
     * The `diffN` constraint ensures that none of these rectangles overlap.
     */
    private fun addNoOverlapConstraint() {
        val taskHeights = Array(numTasks) { model.intVar(1) } // Each task uses 1 employee "unit"
        model.diffN(taskStartTime, taskAssignee, taskDuration, taskHeights, true).post()
    }

    /**
     * Sets initial values for already assigned tasks when the project is in PARTIAL status.
     *
     * For each task that is already assigned in the current project state, this method:
     * - Fixes the employee assignment to the currently assigned employee
     * - Fixes the start time to the current scheduled start time (relative to project kickoff)
     * - Fixes the duration to the current scheduled duration
     *
     * This helps the solver by providing good starting points and reducing the search space.
     */
    private fun setInitialValuesForAssignedTasks() {
        if (project.scheduledStatus() != ProjectScheduled.PARTIAL) return
        val employeeIndexMap = employees.withIndex().associate { it.value.id to it.index }

        for (t in 0 until numTasks) {
            val task = tasks[t]
            if (task is AssignedTask) {
                val empIdx = employeeIndexMap[task.employee.id] ?: continue
                val startOffset = durationUnit(task.startAt - project.kickOff)
                val dur = durationUnit(task.duration)

                if (task.pinned) {
                    model.arithm(taskAssignee[t], "=", empIdx).post()
                    model.arithm(taskStartTime[t], "=", startOffset).post()
                    model.arithm(taskDuration[t], "=", dur).post()
                } else {
                    model.solver.addHint(taskAssignee[t], empIdx)
                    model.solver.addHint(taskStartTime[t], startOffset)
                    model.solver.addHint(taskDuration[t], dur)
                }
            }
        }
    }

    /**
     * Configures a custom search strategy optimized for scheduling problems.
     */
    private fun configureSearchStrategy() {
        val firstFail = FirstFail(model)
        val smallest = Smallest()
        val intDomainMin = IntDomainMin()

        model.solver.setSearch(
            Search.intVarSearch(firstFail, intDomainMin, *taskAssignee),
            Search.intVarSearch(smallest, intDomainMin, *taskStartTime),
        )
    }

    /**
     * Creates the makespan objective variable.
     * The makespan is defined as the maximum end time among all tasks.
     * The model will try to minimize this value.
     * `makespan = max(taskEndTime)`
     *
     * @return The [IntVar] representing the project's makespan.
     */
    private fun createMakespanObjective(): IntVar {
        val totalMinDur = minPossibleTime.sum()
        val lowerBound1 = totalMinDur / numEmployees
        val lowerBound2 = minPossibleTime.maxOrNull() ?: 0
        val lowerBound = lowerBound1.coerceAtLeast(lowerBound2)

        val makespan = model.intVar("makespan", lowerBound, maxPossibleTime)
        model.max(makespan, taskEndTime).post()
        return makespan
    }

    /**
     * Creates the priority cost objective variable.
     * This cost represents the number of "priority inversions," where a lower-priority task
     * is scheduled to start before a higher-priority task. Minimizing this cost helps to
     * align the schedule with the specified task priorities.
     *
     * @return The [IntVar] representing the total priority inversion cost.
     */
    private fun createPriorityCostObjective(): IntVar {
        // A list to hold indicator variables for each potential priority inversion
        val inversionIndicators = mutableListOf<IntVar>()

        // Iterate over all unique pairs of tasks
        for (t1 in 0 until numTasks) {
            for (t2 in t1 + 1 until numTasks) {
                val (p1, p2) = taskPriorities[t1] to taskPriorities[t2]

                // Case 1: Task 1 has lower priority than Task 2 (p1 > p2 means lower priority).
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

        // The total cost is the sum of all priority inversion indicators.
        val priorityCost = model.intVar("priorityCost", 0, numTasks * numTasks)
        if (inversionIndicators.isNotEmpty()) {
            model.sum(inversionIndicators.toTypedArray(), "=", priorityCost).post()
        } else {
            // If there are no pairs with different priorities, cost is always 0.
            model.arithm(priorityCost, "=", 0).post()
        }

        return priorityCost
    }

    /**
     * Computes the earliest possible start time for each task based on precedence constraints.
     * Uses the minimum task duration (best-case across all employees) as an optimistic estimate.
     *
     * @return An array of earliest start times for each task index.
     */
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

    /**
     * Computes the duration matrix for all employee-task pairs.
     *
     * @param employees The list of employees.
     * @param tasks The list of tasks.
     * @param estimator The time estimator.
     * @return A [Result] containing the 2D integer array of durations or an error.
     */
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

    /**
     * Creates a table of precedence constraints from the task dependencies.
     *
     * @param tasks The list of tasks.
     * @return A 2D array where each row `[predecessorIndex, successorIndex]` represents a dependency.
     */
    private fun createPrecedenceTable(tasks: List<Task>): Array<IntArray> {
        val idxTask = tasks.withIndex().associate { (idx, it) -> it.id to idx }
        return tasks
            .filter { it.dependsOn != null }
            .map { tsk ->
                val (a, b) = idxTask.getValue(tsk.dependsOn!!.id) to idxTask.getValue(tsk.id)
                intArrayOf(a, b)
            }.toTypedArray()
    }

    /**
     * Converts a [Duration] to an integer representation (total minutes).
     */
    private fun durationUnit(duration: Duration): Int = duration.inWholeMinutes.toInt()

    /**
     * Converts an integer representation of time (minutes) back to a [Duration].
     */
    private fun unitDuration(duration: Int): Duration = duration.minutes
}
