package io.github.pintowar.rts.core.estimator

import arrow.core.Either
import arrow.core.raise.either
import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.EmployeeId
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.TaskId
import java.time.Duration

interface InvalidMatrixParam
object InvalidEmployeeId: InvalidMatrixParam
object InvalidTaskId: InvalidMatrixParam

class EstimationMatrix private constructor(
    private val employeeIds: Map<EmployeeId, Employee>,
    private val taskIds: Map<TaskId, Task>,
    private val estimator: TimeEstimator
) {

    private val matrix: MutableMap<Pair<EmployeeId, TaskId>, Duration> = mutableMapOf()

    companion object {
        fun valueOf(project: Project, estimator: TimeEstimator): EstimationMatrix {
            val employeeIds = project.employees().associateBy { it.id }
            val taskIds = project.tasks().associateBy { it.id }
            return EstimationMatrix(employeeIds, taskIds, estimator)
        }
    }

    fun duration(employeeId: EmployeeId, taskId: TaskId): Either<InvalidMatrixParam, Duration> = either {
        val employee = employeeIds[employeeId] ?: raise(InvalidEmployeeId)
        val task = taskIds[taskId] ?: raise(InvalidTaskId)
        matrix.getOrPut(employeeId to taskId) {
            estimator.estimate(employee, task)
        }
    }
}