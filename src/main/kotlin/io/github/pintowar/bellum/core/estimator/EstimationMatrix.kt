package io.github.pintowar.bellum.core.estimator

import arrow.core.Either
import arrow.core.getOrElse
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.EmployeeId
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskId
import kotlin.time.Duration

class EstimationMatrix private constructor(
    private val employeeIds: Map<EmployeeId, Employee>,
    private val taskIds: Map<TaskId, Task>,
    private val estimator: TimeEstimator,
) {
    private val matrix: MutableMap<Pair<EmployeeId, TaskId>, Duration> = mutableMapOf()

    companion object {
        operator fun invoke(
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
    ): Either<Throwable, Duration> =
        Either.catch {
            val employee = employeeIds[employeeId] ?: throw IllegalArgumentException("Invalid employee id: $employeeId")
            val task = taskIds[taskId] ?: throw IllegalArgumentException("Invalid task id: $taskId")
            matrix.getOrPut(employeeId to taskId) {
                estimator.estimate(employee, task).getOrElse { throw it }
            }
        }
}
