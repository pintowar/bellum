package io.github.pintowar.rts.core.estimator

import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.SkillPoint
import io.github.pintowar.rts.core.domain.UnassignedTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.mockk.every
import io.mockk.spyk
import kotlin.Result.Companion.success
import kotlin.time.Duration.Companion.minutes

class TimeEstimatorTest :
    FunSpec({

        test("estimate") {
            val timeEstimator = spyk<TimeEstimator>()
            val arg = arrayOf(0, 5, 6, 3)
            every { timeEstimator.skillsEstimation(arg, arg) } returns success(5.minutes)

            val skills = arg.mapIndexed { i, v -> "skill$i" to SkillPoint(v).getOrThrow() }.associate { it }
            val employee = Employee("Alice", skills).getOrThrow()
            val task = UnassignedTask("Task 1", skills = skills).getOrThrow()

            timeEstimator.estimate(employee, task) shouldBeSuccess 5.minutes
        }
    })
