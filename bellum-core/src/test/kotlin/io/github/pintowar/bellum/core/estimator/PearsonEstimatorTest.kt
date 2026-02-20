package io.github.pintowar.bellum.core.estimator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.minutes

class PearsonEstimatorTest :
    FunSpec({
        val estimator = PearsonEstimator()

        test("perfect correlation returns 5 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(1, 2, 3)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result shouldBeSuccess 5.minutes
        }

        test("anti-correlation returns 85 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(3, 2, 1)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result shouldBeSuccess 85.minutes
        }

        test("zero correlation returns 45 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(1, 1, 1)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result shouldBeSuccess 45.minutes
        }

        test("different array lengths throw exception") {
            val employeeSkills = arrayOf(1, 2)
            val taskSkills = arrayOf(1, 2, 3)

            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result.shouldBeFailure {
                it.shouldBeTypeOf<IllegalSkillSets>()
                it.message shouldBe "Skill set from employee (2) and task (3) are not equal."
            }
        }

        test("empty arrays throw exception") {
            val employeeSkills = emptyArray<Int>()
            val taskSkills = emptyArray<Int>()

            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result.shouldBeFailure {
                it.shouldBeTypeOf<IllegalNumSkills>()
                it.message shouldBe "Insufficient employee skills 0, must be at least 2."
            }
        }
    })
