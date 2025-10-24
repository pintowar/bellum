package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.ProjectScheduled
import io.github.pintowar.rts.core.domain.UnassignedTask
import io.github.pintowar.rts.core.estimator.TimeEstimator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant

class ChocoSchedulerTest: FunSpec({

    val task1 = UnassignedTask.valueOf( "Task 1").getOrNull()!!
    val task2 = UnassignedTask.valueOf( "Task 2").getOrNull()!!
    val task3 = UnassignedTask.valueOf( "Task 3", dependsOn = task1).getOrNull()!!
    val task4 = UnassignedTask.valueOf( "Task 4", dependsOn = task2).getOrNull()!!
    val task5 = UnassignedTask.valueOf( "Task 5").getOrNull()!!

    val sampleProject = Project(
        employees = setOf(
            Employee.valueOf( "Employee 1").getOrNull()!!,
            Employee.valueOf( "Employee 2").getOrNull()!!,
            Employee.valueOf( "Employee 3").getOrNull()!!,
        ),
        tasks = setOf(
            task1, task2, task3, task4, task5
        )
    )

    fun mockEstimator(): TimeEstimator {
        val employees = sampleProject.employees()
        val tasks = sampleProject.tasks()
        val timeEstimator = mockk<TimeEstimator>()

        val mtx = listOf(
            listOf(10L, 20, 30, 40, 50), // Employee 0
            listOf(15L, 25, 35, 45, 55), // Employee 1
            listOf(12L, 22, 32, 42, 52)  // Employee 2
        )
        employees.zip(mtx).forEach { (employee, row) ->
            tasks.zip(row).forEach { (task, duration) ->
                every { timeEstimator.estimate(eq(employee), eq(task)) } returns Duration.ofMinutes(duration)
            }
        }
        return timeEstimator
    }

    test("successful solve") {
        val timeEstimator = mockEstimator()
        val solver = ChocoScheduler(timeEstimator)

        val solution = solver.solve(sampleProject, Instant.parse("2022-01-01T00:00:00Z"))

        val (scheduledProject, optimal) = solution
        scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
        optimal shouldBe true

        // check max duration per employee and overall
        // check for overlapping tasks
        // check for missing tasks
    }

})