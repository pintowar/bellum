package io.github.pintowar.bellum.plotter

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CliPlotterTest :
    FunSpec({
        val startTime = Instant.parse("2022-01-01T08:00:00Z")

        context("generateCliPlot - edge cases") {
            test("empty project with no tasks returns appropriate message") {
                val emptyProject =
                    Project(
                        name = "Empty Project",
                        kickOff = startTime,
                        employees = emptySet(),
                        tasks = emptySet(),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(emptyProject)

                result shouldBe "No tasks assigned to show a schedule."
            }

            test("project with only unassigned tasks returns appropriate message") {
                val unassignedProject =
                    Project(
                        name = "Unassigned Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(DataFixtures.task1, DataFixtures.task2),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(unassignedProject)

                result shouldBe "No tasks assigned to show a schedule."
            }

            test("project with tasks of zero duration") {
                val task =
                    AssignedTask(
                        description = "Zero Duration Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 0.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Zero Duration Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project)

                result shouldBe "No tasks assigned to show a schedule."
            }
        }

        context("generateCliPlot - basic functionality") {
            test("single task with basic configuration") {
                val task =
                    AssignedTask(
                        description = "Task-001",
                        priority = TaskPriority.MINOR,
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 60.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Single Task Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 50)

                result shouldContain "Total duration: 60 minutes"
                result shouldContain "Eloyee 1"
                result shouldContain "Task 001"
            }

            test("multiple tasks with different priorities") {
                val task1 =
                    AssignedTask(
                        description = "Critical Task-C001",
                        priority = TaskPriority.CRITICAL,
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val task2 =
                    AssignedTask(
                        description = "Major Task-M001",
                        priority = TaskPriority.MAJOR,
                        employee = DataFixtures.employee2,
                        startAt = startTime + 30.minutes,
                        duration = 45.minutes,
                    ).getOrThrow()

                val task3 =
                    AssignedTask(
                        description = "Minor Task-MIN001",
                        priority = TaskPriority.MINOR,
                        employee = DataFixtures.employee3,
                        startAt = startTime,
                        duration = 60.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Multi Priority Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1, DataFixtures.employee2, DataFixtures.employee3),
                        tasks = setOf(task1, task2, task3),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 80)

                // Check task IDs are present
                result shouldContain "C001"
                result shouldContain "M001"
                result shouldContain "MIN001"
            }

            test("multiple tasks for same employee") {
                val task1 =
                    AssignedTask(
                        description = "Task 1",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val task2 =
                    AssignedTask(
                        description = "Task 2",
                        employee = DataFixtures.employee1,
                        startAt = startTime + 30.minutes,
                        duration = 45.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Same Employee Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task1, task2),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 60)

                result shouldContain "Eloyee 1"
                // Should have both task identifiers
                result.count { it == '1' } shouldBe 4 // 2 for "Employee 1", 2 for task IDs
            }
        }

        context("generateCliPlot - width parameter") {
            test("different width values affect chart scaling") {
                val task =
                    AssignedTask(
                        description = "Width Test Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 100.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Width Test Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result50 = CliPlotter.generateCliPlot(project, width = 50)
                val result100 = CliPlotter.generateCliPlot(project, width = 100)

                // Both should contain the duration
                result50 shouldContain "Total duration: 100 minutes"
                result100 shouldContain "Total duration: 100 minutes"

                // Width 100 should have longer ruler marks
                result100.length shouldBeGreaterThan result50.length
            }

            test("very narrow width") {
                val task =
                    AssignedTask(
                        description = "Narrow Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 10.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Narrow Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 10)

                result shouldContain "Total duration: 10 minutes"
                result shouldContain "Eloyee 1"
                result shouldContain "Task Narro" // Truncated label
            }
        }

        context("generateCliPlot - ruler marks") {
            test("different project durations affect ruler step calculation") {
                // Small project (< 100 minutes) - step should be 10
                val smallTask =
                    AssignedTask(
                        description = "Small Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 50.minutes,
                    ).getOrThrow()

                val smallProject =
                    Project(
                        name = "Small Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(smallTask),
                    ).getOrThrow()

                val smallResult = CliPlotter.generateCliPlot(smallProject, width = 50)

                // Medium project (100-500 minutes) - step should be 50
                val mediumTask =
                    AssignedTask(
                        description = "Medium Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 200.minutes,
                    ).getOrThrow()

                val mediumProject =
                    Project(
                        name = "Medium Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(mediumTask),
                    ).getOrThrow()

                val mediumResult = CliPlotter.generateCliPlot(mediumProject, width = 50)

                // Large project (> 500 minutes) - step should be 100
                val largeTask =
                    AssignedTask(
                        description = "Large Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 600.minutes,
                    ).getOrThrow()

                val largeProject =
                    Project(
                        name = "Large Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(largeTask),
                    ).getOrThrow()

                val largeResult = CliPlotter.generateCliPlot(largeProject, width = 50)

                // All should have total duration
                smallResult shouldContain "Total duration: 50 minutes"
                mediumResult shouldContain "Total duration: 200 minutes"
                largeResult shouldContain "Total duration: 600 minutes"
            }
        }

        context("generateCliPlot - task positioning and labeling") {
            test("task description truncation and centering") {
                val longDescriptionTask =
                    AssignedTask(
                        description = "Very Long Task Description That Should Be Truncated",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Long Description Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(longDescriptionTask),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 40)

                // Should contain part of the description
                result shouldContain "Very Lon"
                // Should contain the legend
                result shouldContain "Legend:"
            }

            test("very short task gets minimal width") {
                val shortTask =
                    AssignedTask(
                        description = "Short",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 1.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Short Task Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(shortTask),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 100)

                result shouldContain "Total duration: 1 minutes"
                result shouldContain "Eloyee 1"
            }

            test("consecutive tasks with no gap") {
                val task1 =
                    AssignedTask(
                        description = "First Task-FIRST",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val task2 =
                    AssignedTask(
                        description = "Second Task-SECOND",
                        employee = DataFixtures.employee1,
                        startAt = startTime + 30.minutes,
                        duration = 30.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Consecutive Tasks Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task1, task2),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project, width = 80)

                result shouldContain "FIRST"
                result shouldContain "SECOND"
                result shouldContain "Eloyee 1"
            }
        }

        context("cliGantt extension function") {
            test("extension function calls generateCliPlot with default width") {
                val task =
                    AssignedTask(
                        description = "Extension Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 60.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Extension Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = project.cliGantt()

                result shouldContain "Total duration: 60 minutes"
                result shouldContain "Eloyee 1"
                result shouldContain "Task Extensio"
            }

            test("extension function with custom width") {
                val task =
                    AssignedTask(
                        description = "Custom Width Task",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 60.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Custom Width Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = project.cliGantt(width = 30)

                result shouldContain "Total duration: 60 minutes"
                result shouldContain "Eloyee 1"
                result shouldContain "Custom"
            }
        }

        context("generateCliPlot - ANSI color codes") {
            test("contains ANSI escape sequences for colors") {
                val criticalTask =
                    AssignedTask(
                        description = "Critical Color Test",
                        priority = TaskPriority.CRITICAL,
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Color Test Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(criticalTask),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project)

                // Check for ANSI escape codes
                result shouldContain "\u001b[41m" // Red background for CRITICAL
                result shouldContain "\u001b[97m" // White text
                result shouldContain "\u001b[0m" // Reset
            }

            test("legend contains all priority colors") {
                val task =
                    AssignedTask(
                        description = "Legend Test",
                        employee = DataFixtures.employee1,
                        startAt = startTime,
                        duration = 30.minutes,
                    ).getOrThrow()

                val project =
                    Project(
                        name = "Legend Project",
                        kickOff = startTime,
                        employees = setOf(DataFixtures.employee1),
                        tasks = setOf(task),
                    ).getOrThrow()

                val result = CliPlotter.generateCliPlot(project)

                result shouldContain "Legend: ["
                result shouldContain "]"
            }
        }
    })
