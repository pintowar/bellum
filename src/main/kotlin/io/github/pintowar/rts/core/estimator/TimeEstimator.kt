package io.github.pintowar.rts.core.estimator

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Task
import java.time.Duration

interface TimeEstimator {

    fun estimate(employeeSkills: Array<Int>, taskSkills: Array<Int>): Duration

    fun estimate(employee: Employee, task: Task): Duration {
        val allSkills = employee.skills.keys + task.requiredSkills.keys
        val employeeSkills = allSkills.map { employee.skills[it]?.let { s -> s() } ?: 0 }.toTypedArray()
        val taskSkills = allSkills.map { task.requiredSkills[it]?.let { s -> s() } ?: 0 }.toTypedArray()

        return estimate(employeeSkills, taskSkills)
    }

}