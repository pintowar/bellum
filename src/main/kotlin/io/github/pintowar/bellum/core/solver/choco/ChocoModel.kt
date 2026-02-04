package io.github.pintowar.bellum.core.solver.choco

import arrow.core.Either
import arrow.core.getOrElse
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import org.chocosolver.solver.Model
import org.chocosolver.solver.ResolutionPolicy
import org.chocosolver.solver.Solution
import org.chocosolver.solver.Solver
import org.chocosolver.solver.search.SearchState
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
 * @param withLexicalConstraint A flag to enable a symmetry-breaking constraint, which can improve performance
 *                              when the project has groups of identical employees.
 */
internal class ChocoModel(
    private val project: Project,
    estimator: TimeEstimator,
    withLexicalConstraint: Boolean = false,
) {
    /**
     * The main Choco Solver model instance.
     */
    private val model = Model("ProjectSchedulerModel")

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
    private val taskDurationMatrix: Array<IntArray> by lazy {
        createDurationMatrix(employees, tasks, estimator).getOrElse { throw it }
    }

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
     * The maximum possible duration for any single task across all employees.
     * This is used to set the upper bound for the `taskDuration` variables.
     */
    private val maxPossibleDuration = taskDurationMatrix.maxOf { it.max() }

    /**
     * An array of variables where `employeeWorkload[e]` represents the total time (in minutes)
     * that employee `e` is busy with assigned tasks.
     */
    private val employeeWorkload = model.intVarArray("employeeWorkload", numEmployees, 0, maxPossibleTime)

    /**
     * An array of variables where `taskStartTime[t]` represents the start time (in minutes, relative to project kick-off)
     * of task `t`.
     */
    private val taskStartTime = model.intVarArray("startTime", numTasks, 0, maxPossibleTime)

    /**
     * An array of variables where `taskDuration[t]` represents the duration (in minutes) of task `t`.
     * This value depends on which employee is assigned to the task.
     */
    private val taskDuration = model.intVarArray("duration", numTasks, 0, maxPossibleDuration)

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

    init {
        // Add constraints to the model
        if (withLexicalConstraint) addSymmetryBreakingConstraints()
        addTaskDurationConstraint()
        addEmployeeWorkloadConstraint()
        addPrecedenceConstraints()
        addNoOverlapConstraint()

        // Set the multi-objective function to minimize.
        // The makespan is given a much higher weight (100) to prioritize it over the priority cost.
        model.setObjective(Model.MINIMIZE, makespan.mul(100).add(priorityCost).intVar())
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
     * @return A [Either] containing the decoded [SchedulerSolution] or an error if decoding fails.
     */
    fun decode(
        solution: Solution,
        currentDuration: Duration,
        optimal: Boolean = false,
    ): Either<Throwable, SchedulerSolution> {
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
     * Adds a lexicographic chain constraint to break symmetries between identical employees.
     *
     * This constraint is applied to tasks that have no predecessors (root tasks).
     * It forces an ordering on the assignment of these tasks to employees who are interchangeable
     * (i.e., have the same skill set and efficiency). This can significantly reduce the search space
     * and improve solver performance.
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
     * Adds a constraint to calculate the total workload for each employee.
     * The workload is the sum of the durations of all tasks assigned to that employee.
     * This is modeled as: `employeeWorkload[e] = sum(duration[t] for all t where taskAssignee[t] = e)`
     */
    private fun addEmployeeWorkloadConstraint() {
        for (e in 0 until numEmployees) {
            // Create an array of variables representing the duration of each task IF assigned to this employee
            val assignedTaskDurations =
                Array(numTasks) { t ->
                    model.intVar("duration_e${e}_t$t", 0, taskDurationMatrix[e][t])
                }

            for (t in 0 until numTasks) {
                // If task 't' is assigned to employee 'e', then its duration is taskDurationMatrix[e][t], else 0.
                model.ifThenElse(
                    model.arithm(taskAssignee[t], "=", e),
                    model.arithm(assignedTaskDurations[t], "=", taskDurationMatrix[e][t]),
                    model.arithm(assignedTaskDurations[t], "=", 0),
                )
            }
            // The employee's total workload is the sum of these conditional durations
            model.sum(assignedTaskDurations, "=", employeeWorkload[e]).post()
        }
    }

    /**
     * Adds constraints to enforce task precedences.
     * For each pair of tasks (A, B) where A is a predecessor of B, it adds the constraint:
     * `taskEndTime[A] <= taskStartTime[B]`
     */
    private fun addPrecedenceConstraints() {
        for (precedence in precedenceConstraints) {
            val predecessor = precedence[0]
            val successor = precedence[1]
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
        model.diffN(taskStartTime, taskAssignee, taskDuration, taskHeights, false).post()
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
        val makespan = model.intVar("makespan", 0, maxPossibleTime)
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
                val p1 = taskPriorities[t1]
                val p2 = taskPriorities[t2]

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
    ): Either<Throwable, Array<IntArray>> =
        Either.catch {
            employees
                .map { emp ->
                    tasks
                        .map { tsk ->
                            estimator.estimate(emp, tsk).fold(
                                ifLeft = { throw it },
                                ifRight = { durationUnit(it) },
                            )
                        }.toIntArray()
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
