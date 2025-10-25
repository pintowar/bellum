package io.github.pintowar.rts.core

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.UnassignedTask
import io.github.pintowar.rts.core.estimator.TimeEstimator
import java.time.Duration

object DataFixtures {
    val task1 = UnassignedTask.valueOf("Task 1").getOrNull()!!
    val task2 = UnassignedTask.valueOf("Task 2").getOrNull()!!
    val task3 = UnassignedTask.valueOf("Task 3", dependsOn = task1).getOrNull()!!
    val task4 = UnassignedTask.valueOf("Task 4", dependsOn = task2).getOrNull()!!
    val task5 = UnassignedTask.valueOf("Task 5").getOrNull()!!

    val employee1 = Employee.valueOf("Employee 1").getOrNull()!!
    val employee2 = Employee.valueOf("Employee 2").getOrNull()!!
    val employee3 = Employee.valueOf("Employee 3").getOrNull()!!

    val sampleProjectSmall =
        Project
            .valueOf(
                employees = setOf(employee1, employee2, employee3),
                tasks = setOf(task1, task2, task3, task4, task5),
            ).getOrThrow()

    fun generateEstimator(
        project: Project,
        mtx: List<List<Long>>,
    ): Map<Employee, Map<Task, Duration>> =
        project.allEmployees().zip(mtx).associate { (employee, row) ->
            val tasks =
                project.allTasks().zip(row).associate { (task, duration) ->
                    task to Duration.ofMinutes(duration)
                }
            employee to tasks
        }

    val smallTimeEstimator =
        object : TimeEstimator {
            private val cache =
                generateEstimator(
                    sampleProjectSmall,
                    listOf(
                        listOf(10L, 20, 30, 40, 50), // Employee 0
                        listOf(15L, 25, 35, 45, 55), // Employee 1
                        listOf(12L, 22, 32, 42, 52), // Employee 2
                    ),
                )

            override fun estimate(
                employeeSkills: Array<Int>,
                taskSkills: Array<Int>,
            ): Duration = TODO("Not yet implemented")

            override fun estimate(
                employee: Employee,
                task: Task,
            ): Duration = cache.getValue(employee).getValue(task)
        }
}
