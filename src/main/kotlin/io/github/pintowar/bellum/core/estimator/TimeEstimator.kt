package io.github.pintowar.bellum.core.estimator

import arrow.core.Either
import arrow.core.getOrElse
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Task
import kotlin.time.Duration

sealed class EstimatorIllegalArgument(
    msg: String,
) : IllegalArgumentException(msg)

class IllegalSkillSets(
    employeeSkills: Int,
    taskSkills: Int,
) : EstimatorIllegalArgument("Skill set from employee ($employeeSkills) and task ($taskSkills) are not equal.")

class IllegalNumSkills(
    kind: String,
    size: Int,
) : EstimatorIllegalArgument("Insufficient $kind skills $size, must be at least 2.")

abstract class TimeEstimator {
    internal abstract fun skillsEstimation(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ): Either<EstimatorIllegalArgument, Duration>

    open fun estimate(
        employee: Employee,
        task: Task,
    ): Either<Throwable, Duration> =
        Either.catch {
            val allSkills = employee.skills.keys + task.requiredSkills.keys
            val employeeSkills =
                allSkills.map { employee.skills[it]?.let { s -> s() } ?: 0 }.toTypedArray()
            val taskSkills = allSkills.map { task.requiredSkills[it]?.let { s -> s() } ?: 0 }.toTypedArray()

            validateSkillSets(employeeSkills, taskSkills).getOrElse { throw it }
            skillsEstimation(employeeSkills, taskSkills).getOrElse { throw it }
        }

    fun validateSkillSets(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ): Either<EstimatorIllegalArgument, Unit> =
        if (employeeSkills.size < 2) {
            Either.Left(IllegalNumSkills("employee", employeeSkills.size))
        } else if (taskSkills.size < 2) {
            Either.Left(IllegalNumSkills("task", taskSkills.size))
        } else if (employeeSkills.size != taskSkills.size) {
            Either.Left(IllegalSkillSets(employeeSkills.size, taskSkills.size))
        } else {
            Either.Right(Unit)
        }
}
