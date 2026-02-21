package io.github.pintowar.bellum.estimator

import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.EmployeeId
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskId
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class CustomEstimator(
    private val matrix: Map<EmployeeId, Map<TaskId, Duration>>,
) : TimeEstimator() {
    companion object {
        private fun generateEstimator(
            project: Project,
            mtx: List<List<Long>>,
        ): Map<EmployeeId, Map<TaskId, Duration>> =
            project.allEmployees().zip(mtx).associate { (employee, row) ->
                val tasks =
                    project.allTasks().zip(row).associate { (task, duration) ->
                        task.id to duration.minutes
                    }
                employee.id to tasks
            }
    }

    constructor(project: Project, matrix: List<List<Long>>) : this(generateEstimator(project, matrix))

    override fun skillsEstimation(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ): Result<Duration> = Result.failure(IllegalStateException("Custom estimator not implemented!"))

    override fun estimate(
        employee: Employee,
        task: Task,
    ): Result<Duration> =
        runCatching {
            matrix.getValue(employee.id).getValue(task.id)
        }
}
