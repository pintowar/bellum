package io.github.pintowar.rts.core.domain

import io.github.pintowar.rts.core.DataFixtures
import io.konform.validation.messagesAtPath
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Duration
import java.time.Instant

class ProjectTest : FunSpec({
    test("scheduledStatus returns NONE when no tasks") {
        val project = Project(emptySet(), emptySet())
        project.scheduledStatus() shouldBe ProjectScheduled.NONE
    }

    test("scheduledStatus returns SCHEDULED when all tasks are assigned") {
        val task1 = mockk<Task>()
        every { task1.isAssigned() } returns true
        val task2 = mockk<Task>()
        every { task2.isAssigned() } returns true
        val project = Project(emptySet(), setOf(task1, task2))
        project.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
    }

    test("scheduledStatus returns PARTIAL when some tasks are assigned") {
        val task1 = mockk<Task>()
        every { task1.isAssigned() } returns true
        val task2 = mockk<Task>()
        every { task2.isAssigned() } returns false
        val project = Project(emptySet(), setOf(task1, task2))
        project.scheduledStatus() shouldBe ProjectScheduled.PARTIAL
    }

    test("scheduledStatus handles empty tasks") {
        val project = Project(emptySet(), emptySet())
        project.scheduledStatus() shouldBe ProjectScheduled.NONE
    }

    test("scheduledStatus handles all tasks unassigned") {
        val task1 = mockk<Task>()
        every { task1.isAssigned() } returns false
        val task2 = mockk<Task>()
        every { task2.isAssigned() } returns false
        val project = Project(emptySet(), setOf(task1, task2))
        project.scheduledStatus() shouldBe ProjectScheduled.NONE
    }

    context("validations") {

        context("check for overlapped tasks for the same employee") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val (dur1, dur2) = Duration.ofMinutes(5) to Duration.ofMinutes(10)

            test("must fail in case of overlap") {
                val tasks = setOf(
                    DataFixtures.task1.assign(DataFixtures.employee1, start, dur1),
                    DataFixtures.task2.assign(DataFixtures.employee1, start, dur2),
                )
                val project = Project(setOf(DataFixtures.employee1), tasks)

                project.isValid() shouldBe false
                val vals = project.validate()
                val msg = "Overlapped tasks for employee: [Employee 1]."
                vals.errors.messagesAtPath(Project::employeesWithOverlap) shouldBe listOf(msg)
            }

            test("must pass in case of no overlap") {
                val tasks = setOf(
                    DataFixtures.task1.assign(DataFixtures.employee1, start, dur1),
                    DataFixtures.task2.assign(DataFixtures.employee1, start + dur1, dur2),
                )
                val project = Project(setOf(DataFixtures.employee1), tasks)

                project.isValid() shouldBe true
                val vals = project.validate()
                vals.errors.size shouldBe 0
            }
        }

        context("check for precedence") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val (dur1, dur2) = Duration.ofMinutes(5) to Duration.ofMinutes(10)

            test("must fail in case of break precedence") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start  + dur2, dur1)
                val tasks = setOf(
                    task1,
                    DataFixtures.task3.copy(dependsOn = task1)
                        .assign(DataFixtures.employee2, start, dur2),
                )
                val project = Project(setOf(DataFixtures.employee1, DataFixtures.employee2), tasks)

                project.isValid() shouldBe false
                val vals = project.validate()
                val msg = "Precedences broken: [Employee 2 (start: 2022-01-01T00:00:00Z) < Employee 1 (end: 2022-01-01T00:10:00Z)]."
                vals.errors.messagesAtPath(Project::precedenceBroken) shouldBe listOf(msg)
            }

            test("must pass in case of NO break precedence") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur1)
                val tasks = setOf(
                    task1,
                    DataFixtures.task3.copy(dependsOn = task1)
                        .assign(DataFixtures.employee2, start + dur1, dur2),
                )
                val project = Project(setOf(DataFixtures.employee1, DataFixtures.employee2), tasks)

                project.isValid() shouldBe true
                val vals = project.validate()
                vals.errors.size shouldBe 0
            }
        }

        context("tasks with circular dependencies") {
            val task5Deps = DataFixtures.task5.copy(dependsOn = DataFixtures.task3)
            val task1Deps = DataFixtures.task1.copy(dependsOn = task5Deps)

            test("must fail in case a circular dependencies is found") {
                val tasks = setOf(task1Deps, DataFixtures.task2, DataFixtures.task3, DataFixtures.task4, task5Deps)
                val project = Project(setOf(DataFixtures.employee1), tasks)

                project.isValid() shouldBe false
                val vals = project.validate()
                val msg = "Circular task dependency found Task 1 - Task 3 - Task 5 - Task 1."
                vals.errors.messagesAtPath(Project::hasCircularTaskDependency) shouldBe listOf(msg)
            }

            test("must pass in case a circular dependencies is not found") {
                val tasks = setOf(DataFixtures.task1, DataFixtures.task2, DataFixtures.task3, DataFixtures.task4, task5Deps)
                val project = Project(setOf(DataFixtures.employee1), tasks)

                project.isValid() shouldBe true
                val vals = project.validate()
                vals.errors.size shouldBe 0
            }
        }

    }
})