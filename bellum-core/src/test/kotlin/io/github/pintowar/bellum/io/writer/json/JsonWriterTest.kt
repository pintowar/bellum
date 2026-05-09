package io.github.pintowar.bellum.io.writer.json

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.core.io.ParsedProject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class JsonWriterTest :
    FunSpec({

        val writer = JsonWriter()

        context("basic project serialization") {
            test("should serialize project with employees and tasks") {
                val project = DataFixtures.sampleProjectSmall

                val parsedProject = ParsedProject(project)
                val result = writer.write(parsedProject)

                result shouldContain "\"name\""
                result shouldContain "Sample Project Small"
                result shouldContain "Employee 1"
                result shouldContain "Employee 2"
                result shouldContain "Task 1"
                result shouldContain "Task 2"
            }

            test("should serialize with correct employee skills array") {
                val emp =
                    Employee(
                        name = "Alice",
                        skills = mapOf("skill1" to SkillPoint(5).getOrThrow(), "skill2" to SkillPoint(3).getOrThrow()),
                    ).getOrThrow()
                val task =
                    UnassignedTask(
                        description = "Task 1",
                        skills = mapOf("skill1" to SkillPoint(0).getOrThrow()),
                    ).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "\"skills\""
                result shouldContain "5"
                result shouldContain "3"
            }

            test("should serialize with correct task required skills array") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
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

                result shouldContain "\"requiredSkills\""
                result shouldContain "3"
                result shouldContain "2"
            }
        }

        context("task dependencies") {
            test("should serialize task with no predecessor as -1") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task = UnassignedTask(description = "Task 1").getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "\"precedes\""
                result shouldContain "-1"
            }

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

                result shouldContain "\"precedes\""
                result shouldContain "1"
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

                result shouldContain "\"priority\""
                result shouldContain "critical"
                result shouldContain "major"
                result shouldContain "minor"
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

                result shouldContain "\"estimationMatrix\""
                result shouldContain "10"
            }

            test("should not include estimation matrix when null") {
                val emp = Employee(name = "Alice").getOrThrow()
                val task = UnassignedTask(description = "Task 1").getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val parsedProject = ParsedProject(project)
                val result = writer.write(parsedProject)

                result shouldNotContain "estimationMatrix"
            }
        }

        context("edge cases") {
            test("should handle employee with skills but no tasks") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = emptySet(),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project))

                result shouldContain "Alice"
            }
        }

        context("omit skills") {
            test("should include both employee skills and task requiredSkills when omitSkills is false") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val task = UnassignedTask(description = "Task 1", skills = mapOf("skill1" to SkillPoint(3).getOrThrow())).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project), omitSkills = false)

                result shouldContain "skills"
                result shouldContain "5"
                result shouldContain "requiredSkills"
                result shouldContain "3"
            }

            test("should omit both employee skills and task requiredSkills when omitSkills is true") {
                val emp = Employee(name = "Alice", skills = mapOf("skill1" to SkillPoint(5).getOrThrow())).getOrThrow()
                val task = UnassignedTask(description = "Task 1", skills = mapOf("skill1" to SkillPoint(3).getOrThrow())).getOrThrow()

                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(emp),
                            tasks = setOf(task),
                        ).getOrThrow()

                val result = writer.write(ParsedProject(project), omitSkills = true)

                result shouldNotContain "skills"
                result shouldContain "Alice"
                result shouldNotContain "requiredSkills"
                result shouldContain "Task 1"
            }
        }
    })
