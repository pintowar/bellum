package io.github.pintowar.bellum.core.estimator

import arrow.core.getOrElse
import arrow.core.right
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.SkillPoint
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.spyk
import kotlin.time.Duration.Companion.minutes

class TimeEstimatorTest :
    FunSpec({

        test("estimate") {
            val timeEstimator = spyk<TimeEstimator>()
            val arg = arrayOf(0, 5, 6, 3)
            every { timeEstimator.skillsEstimation(arg, arg) } returns 5.minutes.right()

            val skills = arg.mapIndexed { i, v -> "skill$i" to SkillPoint(v).getOrElse { throw it } }.associate { it }
            val employee = Employee("Alice", skills).getOrElse { throw it }
            val task = UnassignedTask("Task 1", skills = skills).getOrElse { throw it }

            val result = timeEstimator.estimate(employee, task)
            result.shouldBeRight(5.minutes)
        }
    })
