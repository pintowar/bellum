package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.estimator.TimeEstimator
import kotlinx.datetime.Instant
import org.chocosolver.solver.Model
import org.chocosolver.solver.search.limits.TimeCounter
import org.chocosolver.solver.variables.IntVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class ChocoScheduler(
    override val estimator: TimeEstimator,
) : Scheduler {
    override fun solve(
        project: Project,
        start: Instant,
    ): Result<SchedulerSolution> {
        val employees = project.allEmployees()
        val tasks = project.allTasks()

        val numEmployees = employees.size
        val numTasks = tasks.size
        val precedences = precedenceTable(tasks)
        val matrix = durationMatrix(employees, tasks)

        val modelVars = generateModel(numEmployees, numTasks, matrix, precedences)
        val (model, makespan) = modelVars
        val solver = model.solver

        val timeLimit = TimeCounter(model, 10.seconds.inWholeNanoseconds)
        val (solution, time) =
            measureTimedValue {
                solver.findOptimalSolution(makespan, Model.MINIMIZE, timeLimit)
            }

        val emps = modelVars.taskAssignee.map { employees[solution.getIntVal(it)] }
        val inits = modelVars.taskStartTime.map { start + unitDuration(solution.getIntVal(it)) }
        val durs = modelVars.taskDuration.map { unitDuration(solution.getIntVal(it)) }
        val assigneds = tasks.mapIndexed { idx, tsk -> tsk.assign(emps[idx], inits[idx], durs[idx]) }

        return Project(employees.toSet(), assigneds.toSet()).map { newProject ->
            SchedulerSolution(
                newProject,
                true,
                time,
            )
        }
    }

    fun generateModel(
        numEmployees: Int,
        numTasks: Int,
        matrix: Array<IntArray>,
        precedences: Array<IntArray>,
    ): ModelVars {
        val model = Model("ProjectSchedulerModel")
        // --- Variables ---

        // taskAssignee[t] = e => task 't' is assigned to employee 'e'
        val taskAssignee = model.intVarArray("taskAssignee", numTasks, 0, numEmployees - 1)

        // Calculate a safe upper bound for time-related variables
        val maxPossibleTime = matrix.sumOf { it.sum() }
        val maxPossibleDuration = matrix.map { it.max() }.max()

        // employeeWorkload[e] = total time for employee 'e'
        val employeeWorkload = model.intVarArray("employeeWorkload", numEmployees, 0, maxPossibleTime)

        // Variables for handling precedence constraints
        val taskStartTime = model.intVarArray("startTime", numTasks, 0, maxPossibleTime)
        val taskDuration = model.intVarArray("duration", numTasks, 0, maxPossibleDuration)
        val taskEndTime = Array(numTasks) { i -> taskStartTime[i].add(taskDuration[i]).intVar() }

        // makespan = max(employeeWorkload) -> the variable to minimize
        val makespan = model.intVar("makespan", 0, maxPossibleTime)

        // --- Constraints ---

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

        return ModelVars(
            model = model,
            makespan = makespan,
            taskAssignee = taskAssignee,
            taskStartTime = taskStartTime,
            taskDuration = taskDuration,
        )
    }

    fun durationMatrix(
        employees: List<Employee>,
        tasks: List<Task>,
    ): Array<IntArray> =
        employees
            .map { emp ->
                tasks.map { tsk -> durationUnit(estimator.estimate(emp, tsk)) }.toIntArray()
            }.toTypedArray()

    fun precedenceTable(tasks: List<Task>): Array<IntArray> {
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
        val makespan: IntVar,
        val taskAssignee: Array<IntVar>,
        val taskStartTime: Array<IntVar>,
        val taskDuration: Array<IntVar>,
    )
}
