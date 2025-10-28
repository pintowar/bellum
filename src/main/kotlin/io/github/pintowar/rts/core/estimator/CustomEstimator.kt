package io.github.pintowar.rts.core.estimator

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.EmployeeId
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.TaskId
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
    ): Result<Duration> = throw IllegalStateException("Custom estimator not implemented!")

    override fun estimate(
        employee: Employee,
        task: Task,
    ): Result<Duration> =
        runCatching {
            matrix.getValue(employee.id).getValue(task.id)
        }
}
