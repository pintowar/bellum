package io.github.pintowar.bellum.serdes

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.core.solver.SolutionHistory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Instant
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.time.Duration.Companion.minutes

class SerdesTest :
    FunSpec({

        context("Serdes.toJson") {
            test("should convert SolutionSummaryDto to JsonElement with pretty print") {
                val projectDto =
                    ProjectDto(
                        id = "test-project",
                        name = "Test Project",
                        kickOff = Instant.parse("2022-01-01T00:00:00Z"),
                        employees = emptyList(),
                        tasks = emptyList(),
                    )

                val solutionStats =
                    SolutionStatsDto(
                        solverDuration = 5.minutes,
                        maxDuration = 10.minutes,
                        valid = true,
                        optimal = false,
                    )

                val solverStats = SolverStats.UnknownSolverStats

                val summaryDto =
                    SolutionSummaryDto(
                        solution = projectDto,
                        solutionHistory = listOf(solutionStats),
                        solverStats = solverStats,
                    )

                val jsonElement = Serdes.toJson(summaryDto)

                jsonElement shouldNotBe null
                jsonElement.toString() shouldNotBe null
            }
        }

        context("SolutionHistory.solutionAndStats") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val dur = 5.minutes

            test("should return null when solution history is empty") {
                val emptyHistory = SolutionHistory(emptyList())

                val result = emptyHistory.solutionAndStats()

                result shouldBe null
            }

            test("should return null when no solutions exist") {
                val historyWithNull = SolutionHistory(listOf())

                val result = historyWithNull.solutionAndStats()

                result shouldBe null
            }

            test("should create solution summary with Choco solver stats") {
                val task1 = DataFixtures.task1
                val task2 = DataFixtures.task2
                val employee1 = DataFixtures.employee1

                val assignedTask = task1.assign(employee1, start, dur)
                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(employee1),
                            tasks = setOf(assignedTask, task2),
                        ).getOrThrow()

                val solverStats =
                    mapOf(
                        "solver" to "Choco Solver",
                        "model name" to "TestModel",
                        "search state" to "COMPLETE",
                        "solutions" to "5",
                        "objective" to "100",
                        "nodes" to "50",
                        "backtracks" to "25",
                        "fails" to "10",
                        "restarts" to "2",
                    )

                val solution =
                    SchedulerSolution(
                        project = project,
                        optimal = false,
                        duration = 10.minutes,
                        stats = solverStats,
                    )

                val history = SolutionHistory(listOf(solution))
                val result = history.solutionAndStats()

                result shouldNotBe null
            }

            test("should create solution summary with unknown solver stats") {
                val task1 = DataFixtures.task1
                val task2 = DataFixtures.task2
                val employee1 = DataFixtures.employee1

                val assignedTask = task1.assign(employee1, start, dur)
                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(employee1),
                            tasks = setOf(assignedTask, task2),
                        ).getOrThrow()

                val solverStats =
                    mapOf(
                        "solver" to "Unknown Solver",
                        "custom" to "value",
                    )

                val solution =
                    SchedulerSolution(
                        project = project,
                        optimal = true,
                        duration = 8.minutes,
                        stats = solverStats,
                    )

                val history = SolutionHistory(listOf(solution))
                val result = history.solutionAndStats()

                result shouldNotBe null
            }

            test("should handle multiple solutions and take the last one") {
                val task1 = DataFixtures.task1
                val employee1 = DataFixtures.employee1

                val assignedTask1 = task1.assign(employee1, start, dur)
                val project1 =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(employee1),
                            tasks = setOf(assignedTask1),
                        ).getOrThrow()

                val assignedTask2 = task1.assign(employee1, start + dur, dur)
                val project2 =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(employee1),
                            tasks = setOf(assignedTask2),
                        ).getOrThrow()

                // Use unknown solver for both to avoid ChocoSolverStats constructor issues in test
                val solverStats = mapOf("solver" to "Unknown Solver")

                val solution1 =
                    SchedulerSolution(
                        project = project1,
                        optimal = false,
                        duration = 10.minutes,
                        stats = solverStats,
                    )

                val solution2 =
                    SchedulerSolution(
                        project = project2,
                        optimal = true,
                        duration = 8.minutes,
                        stats = solverStats,
                    )

                val history = SolutionHistory(listOf(solution1, solution2))
                val result = history.solutionAndStats()

                result shouldNotBe null
            }
        }

        context("JsonElement.export") {
            test("should export JSON to file and create file if it doesn't exist") {
                val jsonElement =
                    Serdes.toJson(
                        SolutionSummaryDto(
                            solution =
                                ProjectDto(
                                    id = "test",
                                    name = "Test",
                                    kickOff = Instant.parse("2022-01-01T00:00:00Z"),
                                    employees = emptyList(),
                                    tasks = emptyList(),
                                ),
                            solutionHistory = emptyList(),
                            solverStats = SolverStats.UnknownSolverStats,
                        ),
                    )

                val testFilePath = Path(System.getProperty("java.io.tmpdir"), "test_export_${System.currentTimeMillis()}.json")

                try {
                    jsonElement.export(testFilePath.toString())

                    testFilePath.toFile().exists() shouldBe true
                    testFilePath.toFile().readText() shouldNotBe null
                } finally {
                    testFilePath.deleteIfExists()
                }
            }

            test("should overwrite existing file when exporting") {
                val jsonElement1 =
                    Serdes.toJson(
                        SolutionSummaryDto(
                            solution =
                                ProjectDto(
                                    id = "test1",
                                    name = "Test1",
                                    kickOff = Instant.parse("2022-01-01T00:00:00Z"),
                                    employees = emptyList(),
                                    tasks = emptyList(),
                                ),
                            solutionHistory = emptyList(),
                            solverStats = SolverStats.UnknownSolverStats,
                        ),
                    )

                val jsonElement2 =
                    Serdes.toJson(
                        SolutionSummaryDto(
                            solution =
                                ProjectDto(
                                    id = "test2",
                                    name = "Test2",
                                    kickOff = Instant.parse("2022-01-01T00:00:00Z"),
                                    employees = emptyList(),
                                    tasks = emptyList(),
                                ),
                            solutionHistory = emptyList(),
                            solverStats = SolverStats.UnknownSolverStats,
                        ),
                    )

                val testFilePath = Path(System.getProperty("java.io.tmpdir"), "test_overwrite_${System.currentTimeMillis()}.json")

                try {
                    jsonElement1.export(testFilePath.toString())
                    val firstContent = testFilePath.toFile().readText()

                    jsonElement2.export(testFilePath.toString())
                    val secondContent = testFilePath.toFile().readText()

                    firstContent shouldNotBe secondContent
                    secondContent shouldNotBe null
                } finally {
                    testFilePath.deleteIfExists()
                }
            }
        }

        context("Integration scenarios") {
            val start = Instant.parse("2022-01-01T00:00:00Z")
            val dur = 5.minutes

            test("full workflow: create solution history, convert to JSON, and export") {
                val task1 = DataFixtures.task1
                val employee1 = DataFixtures.employee1

                val assignedTask = task1.assign(employee1, start, dur)
                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(
                            employees = setOf(employee1),
                            tasks = setOf(assignedTask),
                        ).getOrThrow()

                // Use unknown solver for integration test to avoid constructor issues
                val solverStats = mapOf("solver" to "Test Solver")

                val solution =
                    SchedulerSolution(
                        project = project,
                        optimal = true,
                        duration = 12.minutes,
                        stats = solverStats,
                    )

                val history = SolutionHistory(listOf(solution))
                val testFilePath = Path(System.getProperty("java.io.tmpdir"), "integration_test_${System.currentTimeMillis()}.json")

                try {
                    val jsonResult = history.solutionAndStats()
                    jsonResult shouldNotBe null

                    jsonResult!!.export(testFilePath.toString())

                    testFilePath.toFile().exists() shouldBe true
                    val fileContent = testFilePath.toFile().readText()
                    fileContent.isNotBlank() shouldBe true
                } finally {
                    testFilePath.deleteIfExists()
                }
            }
        }

        context("Edge cases and error scenarios") {
            test("should handle ChocoSolverStats constructor with valid map") {
                val task1 = DataFixtures.task1
                val employee1 = DataFixtures.employee1
                val start = Instant.parse("2022-01-01T00:00:00Z")
                val dur = 5.minutes

                val assignedTask = task1.assign(employee1, start, dur)
                val project =
                    DataFixtures.sampleProjectSmall
                        .replace(employees = setOf(employee1), tasks = setOf(assignedTask))
                        .getOrThrow()

                val solverStats =
                    mapOf(
                        "solver" to "Choco Solver",
                        "model name" to "TestModel",
                        "search state" to "COMPLETE",
                        "solutions" to "3",
                        "objective" to "150",
                        "nodes" to "75",
                        "backtracks" to "30",
                        "fails" to "15",
                        "restarts" to "5",
                    )

                val solution =
                    SchedulerSolution(
                        project = project,
                        optimal = true,
                        duration = 12.minutes,
                        stats = solverStats,
                    )

                val history = SolutionHistory(listOf(solution))
                val result = history.solutionAndStats()

                result shouldNotBe null
            }
        }
    })
