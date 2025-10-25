package io.github.pintowar.rts.core.estimator

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.EmployeeId
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.TaskId
import java.time.Duration

class EstimationMatrix private constructor(
    private val employeeIds: Map<EmployeeId, Employee>,
    private val taskIds: Map<TaskId, Task>,
    private val estimator: TimeEstimator,
) {
    private val matrix: MutableMap<Pair<EmployeeId, TaskId>, Duration> = mutableMapOf()

    companion object {
        fun valueOf(
            project: Project,
            estimator: TimeEstimator,
        ): EstimationMatrix {
            val employeeIds = project.allEmployees().associateBy { it.id }
            val taskIds = project.allTasks().associateBy { it.id }
            return EstimationMatrix(employeeIds, taskIds, estimator)
        }
    }

    fun duration(
        employeeId: EmployeeId,
        taskId: TaskId,
    ): Result<Duration> =
        runCatching {
            val employee = employeeIds[employeeId] ?: throw IllegalArgumentException("Invalid employee id: $employeeId")
            val task = taskIds[taskId] ?: throw IllegalArgumentException("Invalid task id: $taskId")
            matrix.getOrPut(employeeId to taskId) {
                estimator.estimate(employee, task)
            }
        }
}
