package io.github.pintowar.bellum.core

import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.estimator.CustomEstimator
import kotlin.getOrThrow
import kotlin.time.Instant

object DataFixtures {
    val task1 = UnassignedTask("Task 1").getOrThrow()
    val task2 = UnassignedTask("Task 2").getOrThrow()
    val task3 = UnassignedTask("Task 3", dependsOn = task1).getOrThrow()
    val task4 = UnassignedTask("Task 4", dependsOn = task2).getOrThrow()
    val task5 = UnassignedTask("Task 5").getOrThrow()

    val employee1 = Employee("Employee 1").getOrThrow()
    val employee2 = Employee("Employee 2").getOrThrow()
    val employee3 = Employee("Employee 3").getOrThrow()

    val sampleProjectSmall =
        Project(
            name = "Sample Project Small",
            kickOff = Instant.parse("2022-01-01T00:00:00Z"),
            employees = setOf(employee1, employee2, employee3),
            tasks = setOf(task1, task2, task3, task4, task5),
        ).getOrThrow()

    val smallMatrix =
        listOf(
            listOf(10L, 20, 30, 40, 50), // Employee 0
            listOf(15L, 25, 35, 45, 55), // Employee 1
            listOf(12L, 22, 32, 42, 52), // Employee 2
        )

    val smallTimeEstimator = CustomEstimator(sampleProjectSmall, smallMatrix)
}
