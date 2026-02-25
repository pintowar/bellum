package io.github.pintowar.bellum.solver.timefold

import ai.timefold.solver.core.api.score.Score
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.solver.timefold.model.SchedulingConstraintProvider
import io.github.pintowar.bellum.solver.timefold.model.SchedulingSolution
import io.github.pintowar.bellum.solver.timefold.model.TaskAssignment
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TimefoldScheduler(
    override val estimator: TimeEstimator,
) : Scheduler() {
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val workers = realNumThreads(numThreads)
            solve(project, timeLimit, workers, callback)
        }

    private fun solve(
        project: Project,
        timeLimit: Duration,
        workers: Int,
        callback: (SchedulerSolution) -> Unit,
    ): SchedulerSolution {
        val solution = buildSchedulingSolution(project)
        val solverFactory: SolverFactory<SchedulingSolution> = createSolverFactory(timeLimit, workers)
        val solver = solverFactory.buildSolver()

        val solvingDuration = Clock.System.now()
        var bestObjective: Score<*> = HardSoftScore.ofHard(Int.MIN_VALUE)
        solver.addEventListener { evt ->
            val (sol, currentObjective) = evt.newBestSolution to evt.newBestScore
            if (currentObjective > bestObjective) {
                bestObjective = currentObjective
                val duration = listOf(timeLimit, Clock.System.now() - solvingDuration).min()
                callback(decodeSolution(project, sol, duration, false))
            }
        }

        val result = solver.solve(solution)

        val duration = listOf(timeLimit, Clock.System.now() - solvingDuration).min()
        return decodeSolution(project, result, duration, false)
    }

    private fun createSolverFactory(
        timeLimit: Duration,
        workers: Int,
    ): SolverFactory<SchedulingSolution> {
        val solverConfig = createSolverConfig(timeLimit, workers)
        return SolverFactory.create(solverConfig)
    }

    private fun createSolverConfig(
        timeLimit: Duration,
        workers: Int,
    ): SolverConfig =
        SolverConfig()
//            .withMoveThreadCount("$workers")
            .withSolutionClass(SchedulingSolution::class.java)
            .withEntityClasses(TaskAssignment::class.java)
            .withConstraintProviderClass(SchedulingConstraintProvider::class.java)
            .withTerminationSpentLimit(java.time.Duration.ofMillis(timeLimit.inWholeMilliseconds))
            .withPhases(
//                ConstructionHeuristicPhaseConfig().withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT),
//                LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.TABU_SEARCH)
            )

    private fun buildSchedulingSolution(project: Project): SchedulingSolution {
        val employees = project.allEmployees()
        val tasks = project.allTasks()

        val durationMap = mutableMapOf<String, Int>()
        for (emp in employees) {
            for (task in tasks) {
                val duration = estimator.estimate(emp, task).getOrThrow()
                val key = "${emp.id()} | ${task.id()}"
                durationMap[key] = duration.inWholeMinutes.toInt()
            }
        }

        val maxPossibleTime = durationMap.values.sum()

        val taskAssignments =
            tasks.map { task ->
                val assignment =
                    if (task is AssignedTask) {
                        TaskAssignment(
                            taskId = task.id(),
                            taskDescription = task.description,
                            taskPriority = task.priority,
                            dependsOnTaskId = task.dependsOn?.id(),
                            requiredDuration = task.duration.inWholeMinutes.toInt(),
                            pinned = task.pinned,
                            pinnedEmployeeId = task.employee.id().toString(),
                            pinnedStartTimeMinute = (task.startAt - project.kickOff).inWholeMinutes.toInt(),
                        )
                    } else {
                        val firstEmp = employees.first()
                        TaskAssignment(
                            taskId = task.id(),
                            taskDescription = task.description,
                            taskPriority = task.priority,
                            dependsOnTaskId = task.dependsOn?.id(),
                            requiredDuration = durationMap["${firstEmp.id()} | ${task.id()}"] ?: 60,
                        )
                    }
                assignment.durationMap = durationMap
                assignment
            }

        val taskRange = (0 until maxPossibleTime).toList()

        return SchedulingSolution(
            employees = employees,
            taskRange = taskRange,
            taskAssignments = taskAssignments,
            durationMap = durationMap,
            maxPossibleTime = maxPossibleTime,
            minPossibleTime = emptyMap(),
            earliestStartTimes = emptyMap(),
            kickOffTime = project.kickOff.toEpochMilliseconds(),
        )
    }

    private fun decodeSolution(
        originalProject: Project,
        solution: SchedulingSolution,
        duration: Duration,
        optimal: Boolean,
    ): SchedulerSolution {
        val kickOffEpochMilli = solution.kickOffTime
        val kickOff = Instant.fromEpochMilliseconds(kickOffEpochMilli)

        val assignedTasks = mutableSetOf<io.github.pintowar.bellum.core.domain.Task>()

        for (assignment in solution.taskAssignments) {
            val employee = assignment.employee
            if (employee != null) {
                val taskDuration = assignment.getDuration()
                val startAt = kickOff + (assignment.startTimeMinute ?: 0).minutes
                val durationDur = taskDuration.toLong().minutes

                val assignedTaskResult =
                    AssignedTask(
                        id = assignment.taskId!!,
                        description = assignment.taskDescription,
                        priority = assignment.taskPriority,
                        employee = employee,
                        startAt = startAt,
                        duration = durationDur,
                        pinned = assignment.pinned,
                    )
                assignedTasks.add(assignedTaskResult.getOrThrow())
            }
        }

        val tasksByBid = assignedTasks.associateBy { it.id }
        val adjustedTasks =
            originalProject
                .allTasks()
                .mapNotNull { task ->
                    tasksByBid[task.id]?.changeDependency(tasksByBid[task.dependsOn?.id])
                }.toSet()
        val newProject = originalProject.replace(tasks = adjustedTasks).getOrThrow()

        val stats =
            mapOf(
                "solver" to "Timefold",
                "score" to (solution.score?.toString() ?: "N/A"),
                "optimal" to optimal,
            )

        return SchedulerSolution(
            project = newProject,
            optimal = optimal,
            duration = duration,
            stats = stats,
        )
    }
}
