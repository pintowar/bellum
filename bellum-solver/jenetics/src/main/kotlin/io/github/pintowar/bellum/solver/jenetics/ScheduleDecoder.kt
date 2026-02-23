package io.github.pintowar.bellum.solver.jenetics

import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import kotlin.time.Duration.Companion.minutes

/**
 * Decodes task permutations into concrete schedules with employee assignments.
 *
 * This class implements a greedy scheduling algorithm that processes tasks in the order
 * specified by a permutation. For each task, it finds the employee who can complete
 * it earliest while respecting dependencies and avoiding overlaps.
 *
 * The decoder handles:
 * - Pinned tasks that must retain their original assignments
 * - Task dependencies ensuring correct execution order
 * - Employee skill matching for task assignments
 * - Timeline management to prevent overlapping assignments
 *
 * @param project The project containing tasks and employees to schedule.
 * @param estimator The time estimator for calculating task durations per employee.
 */
internal class ScheduleDecoder(
    private val project: Project,
    private val estimator: TimeEstimator,
) {
    /**
     * Represents the result of decoding a permutation into a schedule.
     *
     * @property project The project with all tasks assigned to employees.
     * @property fitness The fitness value (lower is better), combining makespan, priority cost, and penalties.
     */
    data class DecodedSchedule(
        val project: Project,
        val fitness: Long,
    )

    private val tasks: List<Task> = project.allTasks()
    private val employees: List<Employee> = project.allEmployees()
    private val numTasks: Int = tasks.size

    /**
     * Decodes a task permutation into a complete schedule.
     *
     * The algorithm:
     * 1. Initializes scheduling state with the given permutation ranking
     * 2. Processes pinned tasks to establish fixed constraints
     * 3. Schedules remaining tasks in permutation order, respecting dependencies
     * 4. Calculates fitness based on makespan and priority inversions
     *
     * @param permutation A list where the value at index i indicates the processing priority of task i.
     * @return A [DecodedSchedule] containing the scheduled project and its fitness.
     */
    fun decode(permutation: List<Int>): DecodedSchedule {
        if (numTasks == 0) {
            return DecodedSchedule(project, 0L)
        }

        val state = initializeDecodingState(permutation)
        processPinnedTasks(state)
        scheduleRemainingTasks(state)
        val fitness = calculateFitness(state)
        val resultProject = buildResultProject(state)

        return DecodedSchedule(resultProject, fitness)
    }

    /**
     * Initializes the decoding state with empty arrays and the permutation ranking.
     *
     * Creates data structures to track:
     * - Task start times, durations, and assignees
     * - The set of unscheduled tasks
     * - Employee timelines for overlap detection
     * - Task dependency relationships
     *
     * @param permutation The task ordering permutation.
     * @return A [DecodingState] ready for scheduling.
     */
    private fun initializeDecodingState(permutation: List<Int>): DecodingState {
        val rank = IntArray(numTasks) { permutation[it] }
        val starts = IntArray(numTasks) { -1 }
        val durations = IntArray(numTasks) { 0 }
        val assignees = IntArray(numTasks) { -1 }
        val unscheduled = mutableSetOf<Int>()
        val timelines = Array(employees.size) { mutableListOf<Pair<Int, Int>>() }
        val dependencies = buildDependencyIndex()

        for (i in 0 until numTasks) {
            val task = tasks[i]
            if (task !is AssignedTask || !task.pinned) {
                unscheduled.add(i)
            }
        }

        return DecodingState(
            rank = rank,
            starts = starts,
            durations = durations,
            assignees = assignees,
            unscheduled = unscheduled,
            timelines = timelines,
            dependencies = dependencies,
        )
    }

    /**
     * Builds an index mapping each task to its dependency's position.
     *
     * @return An array where index i contains the index of task i's dependency, or -1 if none.
     */
    private fun buildDependencyIndex(): IntArray =
        IntArray(numTasks) { i ->
            val depId = tasks[i].dependsOn?.id
            if (depId != null) tasks.indexOfFirst { it.id == depId } else -1
        }

    /**
     * Processes pinned tasks that must retain their original assignments.
     *
     * Pinned tasks are pre-assigned with fixed start times and employees.
     * This method populates their data and adds them to employee timelines
     * so subsequent tasks can avoid overlapping.
     *
     * @param state The decoding state to update with pinned task information.
     */
    private fun processPinnedTasks(state: DecodingState) {
        for (i in 0 until numTasks) {
            val task = tasks[i]
            if (task is AssignedTask && task.pinned) {
                val empIdx = employees.indexOfFirst { it.id == task.employee.id }.takeIf { it >= 0 } ?: 0
                val startOffset = (task.startAt - project.kickOff).inWholeMinutes.toInt()
                val dur = task.duration.inWholeMinutes.toInt()

                state.starts[i] = startOffset
                state.durations[i] = dur
                state.assignees[i] = empIdx
                state.timelines[empIdx].add(startOffset to startOffset + dur)
                state.timelines[empIdx].sortBy { it.first }
            }
        }
    }

    /**
     * Schedules all remaining unpinned tasks in permutation order.
     *
     * Repeatedly finds tasks whose dependencies are satisfied, selects the one
     * with the lowest rank (highest priority in permutation), and assigns it
     * to the best available employee.
     *
     * @param state The decoding state containing unscheduled tasks.
     */
    private fun scheduleRemainingTasks(state: DecodingState) {
        while (state.unscheduled.isNotEmpty()) {
            val available = findAvailableTasks(state)

            if (available.isEmpty()) {
                handleCircularDependency(state)
                break
            }

            val chosen = available.minByOrNull { state.rank[it] }!!
            assignTaskToBestEmployee(chosen, state)
            state.unscheduled.remove(chosen)
        }
    }

    /**
     * Finds tasks whose dependencies have been satisfied.
     *
     * A task is available if it has no dependency, or its dependency has already
     * been scheduled (removed from the unscheduled set).
     *
     * @param state The decoding state containing dependency information.
     * @return A list of task indices ready to be scheduled.
     */
    private fun findAvailableTasks(state: DecodingState): List<Int> =
        state.unscheduled.filter { i ->
            val dep = state.dependencies[i]
            dep == -1 || !state.unscheduled.contains(dep)
        }

    /**
     * Handles circular dependencies by applying heavy penalties.
     *
     * When no tasks are available but some remain unscheduled, a circular
     * dependency exists. This method penalizes the solution and assigns
     * placeholder values to remaining tasks.
     *
     * @param state The decoding state to update.
     */
    private fun handleCircularDependency(state: DecodingState) {
        state.penalty += CIRCULAR_DEPENDENCY_PENALTY
        for (i in state.unscheduled) {
            state.starts[i] = INVALID_START_TIME
            state.durations[i] = 0
            state.assignees[i] = 0
        }
        state.unscheduled.clear()
    }

    /**
     * Assigns a task to the employee who can complete it earliest.
     *
     * Determines the earliest possible start time based on dependencies,
     * then finds the employee who can finish the task first.
     *
     * @param taskIdx The index of the task to assign.
     * @param state The decoding state to update.
     */
    private fun assignTaskToBestEmployee(
        taskIdx: Int,
        state: DecodingState,
    ) {
        val dep = state.dependencies[taskIdx]
        val readyTime = if (dep != -1) state.starts[dep] + state.durations[dep] else 0

        val bestAssignment = findBestEmployeeAssignment(taskIdx, readyTime, state)

        if (bestAssignment == null) {
            handleNoSkilledEmployee(taskIdx, readyTime, state)
        } else {
            applyAssignment(taskIdx, bestAssignment, state)
        }
    }

    /**
     * Finds the best employee assignment for a task.
     *
     * Evaluates all employees who have the required skills, calculating when
     * each could complete the task. Prefers employees who can finish earlier,
     * with tie-breaking in favor of the originally assigned employee (if any).
     *
     * @param taskIdx The index of the task to assign.
     * @param readyTime The earliest time the task can start (after dependencies).
     * @param state The decoding state containing timeline information.
     * @return The best [Assignment] found, or null if no employee has the required skills.
     */
    private fun findBestEmployeeAssignment(
        taskIdx: Int,
        readyTime: Int,
        state: DecodingState,
    ): Assignment? {
        val task = tasks[taskIdx]
        val assignedEmployeeId = (task as? AssignedTask)?.employee?.id
        var best: Assignment? = null

        for (empIdx in employees.indices) {
            val durResult = estimator.estimate(employees[empIdx], task)
            if (durResult.isFailure) continue

            val duration = durResult.getOrThrow().inWholeMinutes.toInt()
            val startTime = findEarliestSlot(empIdx, readyTime, duration, state.timelines)
            val finishTime = startTime + duration
            val isAssignedEmp = assignedEmployeeId == employees[empIdx].id

            if (shouldUpdateBest(best, finishTime, isAssignedEmp)) {
                best = Assignment(empIdx, startTime, duration, finishTime)
            }
        }

        return best
    }

    /**
     * Determines whether a new assignment is better than the current best.
     *
     * An assignment is better if:
     * - There is no current best, or
     * - It finishes earlier, or
     * - It finishes at the same time but matches the originally assigned employee
     *
     * @param current The current best assignment, or null if none exists.
     * @param finishTime The finish time of the candidate assignment.
     * @param isAssignedEmployee Whether this employee was originally assigned to the task.
     * @return True if the candidate should become the new best.
     */
    private fun shouldUpdateBest(
        current: Assignment?,
        finishTime: Int,
        isAssignedEmployee: Boolean,
    ): Boolean {
        if (current == null) return true
        if (finishTime < current.finishTime) return true
        return finishTime == current.finishTime && isAssignedEmployee
    }

    /**
     * Finds the earliest time slot where a task can fit in an employee's timeline.
     *
     * Iterates through the employee's scheduled intervals to find the first gap
     * large enough to accommodate the task duration.
     *
     * @param empIdx The index of the employee.
     * @param readyTime The earliest time the task can start.
     * @param duration The duration of the task.
     * @param timelines Array of employee timelines containing (start, end) pairs.
     * @return The earliest start time that doesn't overlap with existing assignments.
     */
    private fun findEarliestSlot(
        empIdx: Int,
        readyTime: Int,
        duration: Int,
        timelines: Array<MutableList<Pair<Int, Int>>>,
    ): Int {
        val intervals = timelines[empIdx]
        var time = readyTime

        for ((start, end) in intervals) {
            if (time + duration <= start) return time
            if (time < end) time = end
        }

        return time
    }

    /**
     * Handles the case where no employee has the required skills for a task.
     *
     * Applies a heavy penalty and assigns placeholder values to allow
     * scheduling to continue.
     *
     * @param taskIdx The index of the unassignable task.
     * @param readyTime The earliest time the task could start.
     * @param state The decoding state to update.
     */
    private fun handleNoSkilledEmployee(
        taskIdx: Int,
        readyTime: Int,
        state: DecodingState,
    ) {
        state.penalty += NO_SKILL_PENALTY
        state.starts[taskIdx] = readyTime
        state.durations[taskIdx] = DEFAULT_DURATION
        state.assignees[taskIdx] = 0
    }

    /**
     * Applies an assignment to the decoding state.
     *
     * Records the task's start time, duration, and assignee, then updates
     * the employee's timeline to prevent overlapping assignments.
     *
     * @param taskIdx The index of the task being assigned.
     * @param assignment The assignment details.
     * @param state The decoding state to update.
     */
    private fun applyAssignment(
        taskIdx: Int,
        assignment: Assignment,
        state: DecodingState,
    ) {
        state.starts[taskIdx] = assignment.startTime
        state.durations[taskIdx] = assignment.duration
        state.assignees[taskIdx] = assignment.empIdx

        state.timelines[assignment.empIdx].add(assignment.startTime to assignment.finishTime)
        state.timelines[assignment.empIdx].sortBy { it.first }
    }

    /**
     * Calculates the total fitness of the decoded schedule.
     *
     * Fitness combines:
     * - Penalty points for constraint violations
     * - Makespan weighted by [MAKESPAN_WEIGHT]
     * - Priority inversion count
     *
     * Lower fitness values indicate better schedules.
     *
     * @param state The decoding state with complete scheduling information.
     * @return The total fitness value.
     */
    private fun calculateFitness(state: DecodingState): Long {
        val makespan = calculateMakespan(state)
        val priorityCost = calculatePriorityCost(state)

        return state.penalty + makespan * MAKESPAN_WEIGHT + priorityCost
    }

    /**
     * Calculates the project makespan (completion time).
     *
     * @param state The decoding state with task start times and durations.
     * @return The time when the last task finishes, in minutes from project kick-off.
     */
    private fun calculateMakespan(state: DecodingState): Int =
        (0 until numTasks).maxOfOrNull { state.starts[it] + state.durations[it] } ?: 0

    /**
     * Calculates the priority inversion cost.
     *
     * A priority inversion occurs when a lower-priority task starts before
     * a higher-priority task. Each such inversion adds 1 to the cost.
     *
     * @param state The decoding state with task start times and priorities.
     * @return The total number of priority inversions.
     */
    private fun calculatePriorityCost(state: DecodingState): Long {
        var cost = 0L

        for (i in 0 until numTasks) {
            for (j in i + 1 until numTasks) {
                val p1 = tasks[i].priority.value
                val p2 = tasks[j].priority.value
                val s1 = state.starts[i]
                val s2 = state.starts[j]

                if (p1 > p2 && s1 < s2) cost++
                if (p2 > p1 && s2 < s1) cost++
            }
        }

        return cost
    }

    /**
     * Builds the final project with all tasks assigned.
     *
     * Creates a new project where each task is assigned to its designated
     * employee with the calculated start time and duration. Pinned tasks
     * retain their pinned status.
     *
     * @param state The decoding state with complete assignment information.
     * @return A new [Project] with all tasks assigned.
     */
    private fun buildResultProject(state: DecodingState): Project {
        val assignedTasks =
            tasks
                .mapIndexed { idx, task ->
                    val employee = employees[state.assignees[idx]]
                    val startAt = project.kickOff + state.starts[idx].minutes
                    val duration = state.durations[idx].minutes

                    task.assign(employee, startAt, duration).let {
                        if (task is AssignedTask && task.pinned) (it as AssignedTask).pin() else it
                    }
                }.toSet()

        return project.replace(tasks = assignedTasks).getOrThrow()
    }

    /**
     * Mutable state for the decoding process.
     *
     * @property rank Processing priority for each task (lower = higher priority).
     * @property starts Start time for each task, in minutes from project kick-off.
     * @property durations Duration of each task, in minutes.
     * @property assignees Employee index assigned to each task.
     * @property unscheduled Set of task indices not yet scheduled.
     * @property timelines For each employee, a list of (start, end) intervals for assigned tasks.
     * @property dependencies For each task, the index of its dependency, or -1 if none.
     * @property penalty Accumulated penalty points for constraint violations.
     */
    private data class DecodingState(
        val rank: IntArray,
        val starts: IntArray,
        val durations: IntArray,
        val assignees: IntArray,
        val unscheduled: MutableSet<Int>,
        val timelines: Array<MutableList<Pair<Int, Int>>>,
        val dependencies: IntArray,
        var penalty: Long = 0L,
    )

    /**
     * Represents a task assignment to an employee.
     *
     * @property empIdx The index of the assigned employee.
     * @property startTime The start time in minutes from project kick-off.
     * @property duration The task duration in minutes.
     * @property finishTime The finish time (startTime + duration).
     */
    private data class Assignment(
        val empIdx: Int,
        val startTime: Int,
        val duration: Int,
        val finishTime: Int,
    )

    companion object {
        private const val CIRCULAR_DEPENDENCY_PENALTY = 10_000_000L
        private const val NO_SKILL_PENALTY = 1_000_000L
        private const val MAKESPAN_WEIGHT = 100L
        private const val INVALID_START_TIME = 1_000_000
        private const val DEFAULT_DURATION = 10
    }
}
