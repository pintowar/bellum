package io.github.pintowar.rts.core.solver

import io.github.pintowar.rts.core.DataFixtures
import io.github.pintowar.rts.core.domain.ProjectScheduled
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class ChocoSchedulerTest :
    FunSpec({

        val sampleProjectSmall = DataFixtures.sampleProjectSmall
        val smallTimeEstimator = DataFixtures.smallTimeEstimator

        test("successful solve") {
            val solver = ChocoScheduler(smallTimeEstimator)

            val solution = solver.solve(sampleProjectSmall, Instant.parse("2022-01-01T00:00:00Z"))

            val (scheduledProject, optimal) = solution.getOrThrow()
            scheduledProject.scheduledStatus() shouldBe ProjectScheduled.SCHEDULED
            scheduledProject.isValid() shouldBe true
            scheduledProject.endsAt() shouldBe Instant.parse("2022-01-01T01:00:00Z")
            optimal shouldBe true
        }
    })
