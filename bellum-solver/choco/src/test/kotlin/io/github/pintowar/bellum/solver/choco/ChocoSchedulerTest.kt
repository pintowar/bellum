package io.github.pintowar.bellum.solver.choco

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.AssignedTask
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant

class ChocoSchedulerTest :
    FunSpec({

        test("successful solve") {
            val solver = ChocoScheduler(DataFixtures.smallTimeEstimator)

            val solution = solver.findOptimalSchedule(DataFixtures.sampleProjectSmall)

            val (scheduledProject, optimal) = solution.getOrThrow()
            scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
            scheduledProject.isValid() shouldBe true
            scheduledProject.endsAt() shouldBe Instant.Companion.parse("2022-01-01T01:00:00Z")
            optimal shouldBe true
        }

        test("partial project should preserve assigned tasks") {
            val solver = ChocoScheduler(DataFixtures.smallTimeEstimator)

            val solution = solver.findOptimalSchedule(DataFixtures.samplePartialProjectSmall)

            val (scheduledProject, optimal) = solution.getOrThrow()
            scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
            scheduledProject.isValid() shouldBe true
            scheduledProject.endsAt() shouldBe Instant.Companion.parse("2022-01-01T01:00:00Z")
            optimal shouldBe true
        }

        test("partial pinned project should preserve assigned tasks") {
            val solver = ChocoScheduler(DataFixtures.smallTimeEstimator)

            val solution = solver.findOptimalSchedule(DataFixtures.samplePartialPinnedProjectSmall)

            val (scheduledProject, _) = solution.getOrThrow()
            scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
            scheduledProject.isValid() shouldBe true

            val allTasks = scheduledProject.allTasks()
            allTasks.size shouldBe 5

            val task1Result = allTasks.find { it.id == DataFixtures.task1.id }
            task1Result shouldNotBe null
            val assignedTask1 = task1Result as AssignedTask
            assignedTask1.startAt shouldBe DataFixtures.assignedTask1.startAt
            assignedTask1.employee.name shouldBe DataFixtures.assignedTask1.employee.name
            assignedTask1.duration shouldBe DataFixtures.assignedTask1.duration
        }
    })
