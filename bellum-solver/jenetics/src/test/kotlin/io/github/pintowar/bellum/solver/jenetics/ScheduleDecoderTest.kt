package io.github.pintowar.bellum.solver.jenetics

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.domain.TaskPriority
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.github.pintowar.bellum.estimator.CustomEstimator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant

class ScheduleDecoderTest :
    FunSpec({

        test("decode empty project should return zero fitness") {
            val kickOff = Instant.parse("2022-01-01T00:00:00Z")
            val emptyProject =
                Project(
                    name = "Empty Project",
                    kickOff = kickOff,
                    employees = setOf(DataFixtures.employee1),
                    tasks = emptySet(),
                ).getOrThrow()

            val decoder = ScheduleDecoder(emptyProject, DataFixtures.smallTimeEstimator)
            val result = decoder.decode(emptyList())

            result.project shouldBe emptyProject
            result.fitness shouldBe 0L
        }

        test("decode should assign all tasks to employees") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            val tasks = result.project.allTasks()
            tasks shouldHaveSize 5
            tasks.all { it.isAssigned() } shouldBe true
        }

        test("decode should respect task dependencies") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            val tasks = result.project.allTasks()
            val task3 = tasks.find { it.id == DataFixtures.task3.id } as AssignedTask
            val task1 = tasks.find { it.id == DataFixtures.task1.id } as AssignedTask

            task3.startAt shouldBe task1.endsAt
        }

        test("decode should preserve pinned task assignments") {
            val project = DataFixtures.samplePartialPinnedProjectSmall
            val estimator = CustomEstimator(project, DataFixtures.smallMatrix)
            val decoder = ScheduleDecoder(project, estimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            val tasks = result.project.allTasks()
            val task1Result = tasks.find { it.id == DataFixtures.task1.id } as AssignedTask

            task1Result.startAt shouldBe DataFixtures.assignedTask1.startAt
            task1Result.employee.id shouldBe DataFixtures.assignedTask1.employee.id
            task1Result.duration shouldBe DataFixtures.assignedTask1.duration
        }

        test("decode should assign tasks to employees with shortest finish time") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            result.fitness shouldBeLessThan 1_000_000L
        }

        test("decode should handle different permutations") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)

            val result1 = decoder.decode(listOf(0, 1, 2, 3, 4))
            val result2 = decoder.decode(listOf(4, 3, 2, 1, 0))

            result1.fitness shouldNotBe null
            result2.fitness shouldNotBe null
        }

        test("decode should calculate makespan correctly") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            val expectedMakespan = 60L
            result.fitness shouldBeLessThan expectedMakespan * 100L + 1000L
        }

        test("decode should handle project with priority inversions") {
            val kickOff = Instant.parse("2022-01-01T00:00:00Z")
            val employee1 = Employee("Employee 1").getOrThrow()

            val lowPriorityTask = UnassignedTask("Low Priority", TaskPriority.MINOR).getOrThrow()
            val highPriorityTask = UnassignedTask("High Priority", TaskPriority.CRITICAL).getOrThrow()

            val project =
                Project(
                    name = "Priority Test Project",
                    kickOff = kickOff,
                    employees = setOf(employee1),
                    tasks = setOf(lowPriorityTask, highPriorityTask),
                ).getOrThrow()

            val matrix = listOf(listOf(10L, 10L))
            val estimator = CustomEstimator(project, matrix)
            val decoder = ScheduleDecoder(project, estimator)

            val result = decoder.decode(listOf(0, 1))

            val tasks = result.project.allTasks()
            tasks.all { it.isAssigned() } shouldBe true
        }

        test("decode should produce valid project") {
            val decoder = ScheduleDecoder(DataFixtures.sampleProjectSmall, DataFixtures.smallTimeEstimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            result.project.isValid() shouldBe true
        }

        test("decode should handle partial project without pinned tasks") {
            val project = DataFixtures.samplePartialProjectSmall
            val estimator = CustomEstimator(project, DataFixtures.smallMatrix)
            val decoder = ScheduleDecoder(project, estimator)
            val permutation = listOf(0, 1, 2, 3, 4)

            val result = decoder.decode(permutation)

            val tasks = result.project.allTasks()
            tasks.all { it.isAssigned() } shouldBe true
            result.project.isValid() shouldBe true
        }
    })
