package io.github.pintowar.rts.core.estimator

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Task
import java.time.Duration

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
    ): Result<Duration>

    open fun estimate(
        employee: Employee,
        task: Task,
    ): Result<Duration> =
        runCatching {
            val allSkills = employee.skills.keys + task.requiredSkills.keys
            val employeeSkills = allSkills.map { employee.skills[it]?.let { s -> s() } ?: 0 }.toTypedArray()
            val taskSkills = allSkills.map { task.requiredSkills[it]?.let { s -> s() } ?: 0 }.toTypedArray()

            check(employeeSkills, taskSkills)
            return skillsEstimation(employeeSkills, taskSkills)
        }

    fun check(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ) {
        if (employeeSkills.size < 2) throw IllegalNumSkills("employee", employeeSkills.size)
        if (taskSkills.size < 2) throw IllegalNumSkills("task", taskSkills.size)
        if (employeeSkills.size != taskSkills.size) throw IllegalSkillSets(employeeSkills.size, taskSkills.size)
    }
}
