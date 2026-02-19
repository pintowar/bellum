package io.github.pintowar.bellum.core.solver.choco

import io.github.pintowar.bellum.core.DataFixtures
import io.github.pintowar.bellum.core.domain.ProjectScheduled
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class ChocoSchedulerTest :
    FunSpec({

        test("successful solve") {
            val solver = ChocoScheduler(DataFixtures.smallTimeEstimator)

            val solution = solver.findOptimalSchedule(DataFixtures.sampleProjectSmall)

            val (scheduledProject, optimal) = solution.getOrThrow()
            scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
            scheduledProject.isValid() shouldBe true
            scheduledProject.endsAt() shouldBe Instant.parse("2022-01-01T01:00:00Z")
            optimal shouldBe true
        }
    })
