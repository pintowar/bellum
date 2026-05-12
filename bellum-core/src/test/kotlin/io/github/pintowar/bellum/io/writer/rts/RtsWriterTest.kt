package io.github.pintowar.bellum.io.writer.rts

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.io.ParsedProject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class RtsWriterTest :
    FunSpec({

        val writer = RtsWriter()

        context("basic project serialization") {
            test("should serialize project with employees and tasks") {
                val emp1 = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val emp2 = Employee(name = "Bob", skills = mapOf("skill1" to SkillPoint(2).getOrThrow())).getOrThrow()

                val task1 =
                    UnassignedTask(
                        description = "Task 1",
                        priority = TaskPriority.MINOR,
                        skills =
                            mapOf(
                                "skill1" to SkillPoint(3).getOrThrow(),
                            ),
                    ).getOrThrow()
                val task2 =
                    UnassignedTask(
                        description = "Task 2",
                        priority = TaskPriority.MAJOR,
                        skills =
                            mapOf(
                                "skill1" to SkillPoint(1).getOrThrow(),
                            ),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp1, emp2),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()

                val parsedProject = ParsedProject(project)
                val result = writer.write(parsedProject)

                result shouldContain "Alice"
                result shouldContain "Bob"
                result shouldContain "Task 1"
                result shouldContain "Task 2"
            }

            test("should serialize employee section with header") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val task =
                    UnassignedTask(
                        description = "Task 1",
                        skills = mapOf("skill1" to SkillPoint(3).getOrThrow()),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "id,content,skill1"
                result shouldContain "1,Alice,5"
            }

            test("should serialize task section with header") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val task =
                    UnassignedTask(
                        description = "Task 1",
                        priority = TaskPriority.MINOR,
                        skills =
                            mapOf(
                                "skill1" to SkillPoint(3).getOrThrow(),
                            ),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "id,content,priority,precedes,skill1"
                result shouldContain "1,Task 1,minor,-1,3"
            }
        }

        context("multiple skills") {
            test("should handle multiple skills in employee and task") {
                val emp =
                    Employee(
                        name = "Alice",
                        skills = mapOf("skill1" to SkillPoint(5).getOrThrow(), "skill2" to SkillPoint(3).getOrThrow()),
                    ).getOrThrow()
                val task =
                    UnassignedTask(
                        description = "Task 1",
                        skills =
                            mapOf(
                                "skill1" to SkillPoint(3).getOrThrow(),
                                "skill2" to SkillPoint(2).getOrThrow(),
                            ),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "skill1,skill2"
                result shouldContain "Alice,5,3"
                result shouldContain "Task 1,minor,-1,3,2"
            }

            test("should align skill columns between employees and tasks") {
                val emp1 = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val emp2 = Employee(name = "Bob", skills = mapOf("skill2" to SkillPoint(3).getOrThrow())).getOrThrow()
                val task =
                    UnassignedTask(
                        description = "Task 1",
                        skills =
                            mapOf(
                                "skill1" to SkillPoint(2).getOrThrow(),
                                "skill2" to SkillPoint(1).getOrThrow(),
                            ),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp1, emp2),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "skill1,skill2"
            }
        }

        context("task dependencies") {
            test("should serialize task with predecessor correctly") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task1 = UnassignedTask(description = "Task 1").getOrThrow()
                val task2 = UnassignedTask(description = "Task 2", dependsOn = task1).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task1, task2),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "2,Task 2,minor,1"
            }
        }

        context("priorities") {
            test("should serialize priority in lowercase") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task1 = UnassignedTask(description = "Task 1", priority = TaskPriority.CRITICAL).getOrThrow()
                val task2 = UnassignedTask(description = "Task 2", priority = TaskPriority.MAJOR).getOrThrow()
                val task3 = UnassignedTask(description = "Task 3", priority = TaskPriority.MINOR).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task1, task2, task3),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain ",critical,"
                result shouldContain ",major,"
                result shouldContain ",minor,"
            }
        }

        context("section separator") {
            test("should include separator between employee and task sections") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task = UnassignedTask(description = "Task 1").getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "================="
            }
        }

        context("estimation matrix") {
            test("should serialize estimation matrix when present") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task = UnassignedTask(description = "Task 1").getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val matrix = listOf(listOf(10L))
                val parsedProject = ParsedProject(project, matrix)
                val result = writer.write(parsedProject)

                result shouldContain "10"
                result shouldContain "================="
            }
        }

        context("edge cases") {
            test("should handle employee with tasks but no skills") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task = UnassignedTask(description = "Task 1").getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "Alice"
                result shouldContain "Task 1"
            }
        }
    })
