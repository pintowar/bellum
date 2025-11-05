package io.github.pintowar.bellum.core.domain

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.DataFixtures.sampleProjectSmall
import io.konform.validation.messagesAtPath
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class ProjectTest :
    FunSpec({
        context("describe") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val dur = 5.minutes

            test("empty project with no tasks") {
                val project = sampleProjectSmall.replace(employees = emptySet(), tasks = emptySet()).getOrThrow()
                val description = project.describe()

                description shouldContain "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: null."
                description shouldNotContain "Employee"
            }

            test("project with only unassigned tasks") {
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                            tasks = setOf(DataFixtures.task1, DataFixtures.task2, DataFixtures.task3),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: null."
                description shouldNotContain "Employee"
            }

            test("project with single task assigned to one employee") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = setOf(task1, DataFixtures.task2, DataFixtures.task3),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: 5m."
                description shouldContain "-------"
                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m]"
            }

            test("project with multiple tasks assigned to same employee") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2.assign(DataFixtures.employee1, start + dur, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: 10m."
                description shouldContain "-------"
                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m, Task 2 (MINOR) - 5m]"
            }

            test("project with tasks assigned to multiple employees") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2.assign(DataFixtures.employee2, start, dur)
                val task3 = DataFixtures.task3.assign(DataFixtures.employee3, start, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2, DataFixtures.employee3),
                            tasks = setOf(task1, task2, task3),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: 5m."
                description shouldContain "-------"
                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m]"
                description shouldContain "Employee 2: [Task 2 (MINOR) - 5m]"
                description shouldContain "Employee 3: [Task 3 (MINOR) - 5m]"
            }

            test("project with different task priorities") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val taskWithDifferentPriority = UnassignedTask("Task with CRITICAL priority", priority = TaskPriority.CRITICAL).getOrThrow()
                val task2 = taskWithDifferentPriority.assign(DataFixtures.employee1, start + dur, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m, Task with CRITICAL priority (CRITICAL) - 5m]"
            }

            test("project with different task durations") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2.assign(DataFixtures.employee1, start + dur, 15.minutes)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m, Task 2 (MINOR) - 15m]"
                description shouldContain "Max duration: 20m"
            }

            test("project with tasks having dependencies") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task3 = DataFixtures.task3.assign(DataFixtures.employee2, start + dur, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                            tasks = setOf(task1, task3),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "-------"
                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m]"
                description shouldContain "Employee 2: [Task 3 (MINOR) - 5m]"
            }

            test("project with special characters in names") {
                val taskWithSpecialChars = UnassignedTask("Task with \"quotes\" & <tags>").getOrThrow()
                val employeeWithSpecialChars = Employee("Employee with 'apostrophe'").getOrThrow()
                val task1 = taskWithSpecialChars.assign(employeeWithSpecialChars, start, dur)
                val project =
                    Project(
                        name = "Project with \"quotes\" & <special>",
                        kickOff = start,
                        employees = setOf(employeeWithSpecialChars),
                        tasks = setOf(task1),
                    ).getOrThrow()
                val description = project.describe()

                description shouldContain
                    "Project: Project with \"quotes\" & <special> (starting at 2022-01-01T00:00:00Z). Max duration: 5m."
                description shouldContain "-------"
                description shouldContain "Employee with 'apostrophe': [Task with \"quotes\" & <tags> (MINOR) - 5m]"
            }

            test("project with many tasks displays correctly") {
                val tasks =
                    (1..10).map { i ->
                        UnassignedTask("Task $i")
                            .getOrThrow()
                            .assign(DataFixtures.employee1, start + ((i - 1) * 5).minutes, dur)
                    }
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = tasks.toSet(),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain
                    "Employee 1: [Task 1 (MINOR) - 5m, Task 2 (MINOR) - 5m, Task 3 (MINOR) - 5m, Task 4 (MINOR) - 5m, Task 5 (MINOR) - 5m, Task 6 (MINOR) - 5m, Task 7 (MINOR) - 5m, Task 8 (MINOR) - 5m, Task 9 (MINOR) - 5m, Task 10 (MINOR) - 5m]"
            }

            test("project with no assigned tasks shows null duration") {
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                            tasks = setOf(DataFixtures.task1, DataFixtures.task2),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Max duration: null."
            }

            test("describe format consistency") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val task2 = DataFixtures.task2.assign(DataFixtures.employee2, start + dur, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1, DataFixtures.employee2),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()
                val description = project.describe()

                // Check basic format structure
                description.lines().size shouldBe 4
                description.lines()[0] shouldBe "Project: Sample Project Small (starting at 2022-01-01T00:00:00Z). Max duration: 10m."
                description.lines()[1] shouldBe "-------"
                description.lines()[2] shouldBe "Employee 1: [Task 1 (MINOR) - 5m]"
                description.lines()[3] shouldBe "Employee 2: [Task 2 (MINOR) - 5m]"
            }

            test("mixed assigned and unassigned tasks") {
                val task1 = DataFixtures.task1.assign(DataFixtures.employee1, start, dur)
                val project =
                    sampleProjectSmall
                        .replace(
                            employees = setOf(DataFixtures.employee1),
                            tasks = setOf(task1, DataFixtures.task2, DataFixtures.task3),
                        ).getOrThrow()
                val description = project.describe()

                description shouldContain "Employee 1: [Task 1 (MINOR) - 5m]"
                description shouldNotContain "Task 2"
                description shouldNotContain "Task 3"
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
                    vals.errors.messagesAtPath(Project::employeesWithOverlap) shouldBe listOf(msg)
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
                    vals.errors.messagesAtPath(Project::precedenceBroken) shouldBe listOf(msg)
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
