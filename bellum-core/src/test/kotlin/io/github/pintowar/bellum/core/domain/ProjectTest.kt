package io.github.pintowar.bellum.core.domain

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.DataFixtures.sampleProjectSmall
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProjectTest :
    FunSpec({
        context("priorityCost") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val dur = 5.minutes

            test("should be zero when no priority inversions exist") {
                val t1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val t2 = DataFixtures.task2.assign(DataFixtures.employee2, start + dur, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                            tasks = setOf(t1, t2),
                        ).getOrThrow()

                project.priorityCost shouldBe 0L
            }

            test("should compute cost when lower priority task starts earlier") {
                val tCritical = UnassignedTask("Task CRITICAL", priority = TaskPriority.CRITICAL).getOrThrow()
                val tMinor = UnassignedTask("Task MINOR", priority = TaskPriority.MINOR).getOrThrow()

                // Minor starts first, Critical starts later -> inversion!
                val t1 = tMinor.assign(DataFixtures.employee1, start, dur)
                val t2 = tCritical.assign(DataFixtures.employee2, start + dur, dur)

                val project =
                    Project(
                        name = "P",
                        kickOff = start,
                        employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                        tasks = setOf(t1, t2),
                    ).getOrThrow()

                project.priorityCost shouldBe 1L
            }
        }

        context("scheduledStatus") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val dur = 5.minutes

            test("scheduledStatus returns NONE when no tasks") {
                val project = sampleProjectSmall.replace(employees = emptySet(), tasks = emptySet())
                project shouldBeSuccess {
                    it.scheduledStatus() shouldBe ProjectScheduled.NONE
                }
            }

            test("scheduledStatus returns SCHEDULED when all tasks are assigned") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2.assign(DataFixtures.employee2, start, dur)
                val project =
                    sampleProjectSmall.replace(
                        employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                        tasks = setOf(task1, task2),
                    )
                project shouldBeSuccess {
                    it.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
                }
            }

            test("scheduledStatus returns PARTIAL when some tasks are assigned") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2
                val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee1), tasks = setOf(task1, task2))
                project shouldBeSuccess {
                    it.scheduledStatus() shouldBe ProjectScheduled.PARTIAL
                }
            }

            test("scheduledStatus handles empty tasks") {
                val project = sampleProjectSmall.replace(employees = emptySet(), tasks = emptySet())
                project shouldBeSuccess {
                    it.scheduledStatus() shouldBe ProjectScheduled.NONE
                }
            }

            test("scheduledStatus handles all tasks unassigned") {
                val task1 = DataFixtures.task1
                val task2 = DataFixtures.task2
                val project = sampleProjectSmall.replace(employees = emptySet(), tasks = setOf(task1, task2))
                project shouldBeSuccess {
                    it.scheduledStatus() shouldBe ProjectScheduled.NONE
                }
            }
        }

        context("validations") {

            context("check for overlapped tasks for the same employee") {
                val start = Instant.parse("2022-01-01T00:00:00Z")
                val (dur1, dur2) = 5.minutes to 10.minutes

                test("must fail in case of overlap") {
                    val tasks =
                        setOf(
                            DataFixtures.task1.assign(DataFixtures.employee1, start, dur1),
                            DataFixtures.task2.assign(DataFixtures.employee1, start, dur2),
                        )
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee1), tasks = tasks).getOrThrow()

                    project.isValid() shouldBe false
                    val vals = project.validate()
                    val msg = "Overlapped tasks for employee: [Employee 1]."
                    vals.errors.messagesAtPath(Project::hasEmployeesWithOverlap) shouldBe listOf(msg)
                }

                test("must pass in case of no overlap") {
                    val tasks =
                        setOf(
                            DataFixtures.task1.assign(DataFixtures.employee1, start, dur1),
                            DataFixtures.task2.assign(DataFixtures.employee1, start + dur1, dur2),
                        )
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee1), tasks = tasks).getOrThrow()

                    project.isValid() shouldBe true
                    val vals = project.validate()
                    vals.errors.size shouldBe 0
                }
            }

            context("check for precedence") {
                val start = Instant.parse("2022-01-01T00:00:00Z")
                val (dur1, dur2) = 5.minutes to 10.minutes

                test("must fail in case of break precedence") {
                    val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start + dur2, dur1)
                    val tasks =
                        setOf(
                            task1,
                            DataFixtures.task3
                                .changeDependency(dependsOn = task1)
                                .assign(DataFixtures.employee2, start, dur2),
                        )
                    val project =
                        sampleProjectSmall
                            .replace(
                                employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                                tasks = tasks,
                            ).getOrThrow()

                    project.isValid() shouldBe false
                    val vals = project.validate()
                    val msg = "Precedences broken: [Employee 2 (start: 2022-01-01T00:00:00Z) < Employee 1 (end: 2022-01-01T00:10:00Z)]."
                    vals.errors.messagesAtPath(Project::hasPrecedenceBroken) shouldBe listOf(msg)
                }

                test("must pass in case of NO break precedence") {
                    val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur1)
                    val tasks =
                        setOf(
                            task1,
                            DataFixtures.task3
                                .changeDependency(dependsOn = task1)
                                .assign(DataFixtures.employee2, start + dur1, dur2),
                        )
                    val project =
                        sampleProjectSmall
                            .replace(
                                employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                                tasks = tasks,
                            ).getOrThrow()

                    project.isValid() shouldBe true
                    val vals = project.validate()
                    vals.errors.size shouldBe 0
                }
            }

            context("check for tasks with circular dependencies") {
                val task5Deps = DataFixtures.task5.changeDependency(dependsOn = DataFixtures.task3)
                val task1Deps = DataFixtures.task1.changeDependency(dependsOn = task5Deps)

                test("must fail while initializing in case a circular dependencies is found") {
                    val tasks = setOf(task1Deps, DataFixtures.task2, DataFixtures.task3, DataFixtures.task4, task5Deps)
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee1), tasks = tasks)

                    project.shouldBeFailure<ValidationException>()
                    if (project.isFailure) {
                        val ex = project.exceptionOrNull() as ValidationException
                        val msg = "Circular task dependency found Task 1 - Task 3 - Task 5 - Task 1."
                        ex.errors.size shouldBe 1
                        ex.errors.messagesAtPath(Project::hasCircularTaskDependency) shouldBe listOf(msg)
                    }
                }

                test("must pass in case a circular dependencies is not found") {
                    val tasks =
                        setOf(DataFixtures.task1, DataFixtures.task2, DataFixtures.task3, DataFixtures.task4, task5Deps)
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee1), tasks = tasks).getOrThrow()

                    project.isValid() shouldBe true
                    val vals = project.validate()
                    vals.errors.size shouldBe 0
                }
            }

            context("check for tasks with invalid employee") {
                val start = Instant.parse("2022-01-01T00:00:00Z")
                val dur = 5.minutes

                test("must fail while initializing in case a invalid employee is found") {
                    val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                    val tasks = setOf(task1, DataFixtures.task2, DataFixtures.task3)
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee2), tasks = tasks)

                    project.shouldBeFailure<ValidationException>()
                    if (project.isFailure) {
                        val ex = project.exceptionOrNull() as ValidationException
                        val msg = "Some tasks are assigned to employees out of the project: [Employee 1]."
                        ex.errors.size shouldBe 1
                        ex.errors.messagesAtPath(Project::hasUnknownEmployees) shouldBe listOf(msg)
                    }
                }

                test("must pass while initializing in case a invalid employee is not found") {
                    val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                    val tasks = setOf(task1, DataFixtures.task2, DataFixtures.task3)
                    val project =
                        sampleProjectSmall
                            .replace(
                                employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                                tasks = tasks,
                            ).getOrThrow()

                    project.isValid() shouldBe true
                    val vals = project.validate()
                    vals.errors.size shouldBe 0
                }
            }

            context("check for missing task dependencies") {
                val start = Instant.parse("2022-01-01T00:00:00Z")
                val dur = 5.minutes

                test("must fail while initializing in case a missing task dependency is found") {
                    val tasks = setOf(DataFixtures.task2, DataFixtures.task3, DataFixtures.task4)
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee2), tasks = tasks)

                    project.shouldBeFailure<ValidationException>()
                    if (project.isFailure) {
                        val ex = project.exceptionOrNull() as ValidationException
                        val msg = "Following task dependencies were not found: Task 1."
                        ex.errors.size shouldBe 1
                        ex.errors.messagesAtPath(Project::hasMissingTaskDependencies) shouldBe listOf(msg)
                    }
                }

                test("must pass while initializing in case a missing task dependency is not found") {
                    val tasks = setOf(DataFixtures.task1, DataFixtures.task2, DataFixtures.task3)
                    val project = sampleProjectSmall.replace(employees = setOf(DataFixtures.employee2), tasks = tasks).getOrThrow()

                    project.isValid() shouldBe true
                    val vals = project.validate()
                    vals.errors.size shouldBe 0
                }
            }
        }
    })
