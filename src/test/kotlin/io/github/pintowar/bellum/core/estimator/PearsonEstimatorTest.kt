package io.github.pintowar.bellum.core.estimator

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.minutes

class PearsonEstimatorTest :
    FunSpec({
        val estimator = PearsonEstimator()

        test("perfect correlation returns 5 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(1, 2, 3)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result.shouldBeRight(5.minutes)
        }

        test("anti-correlation returns 85 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(3, 2, 1)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result.shouldBeRight(85.minutes)
        }

        test("zero correlation returns 45 minutes") {
            val employeeSkills = arrayOf(1, 2, 3)
            val taskSkills = arrayOf(1, 1, 1)
            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            result.shouldBeRight(45.minutes)
        }

        test("different array lengths throw exception") {
            val employeeSkills = arrayOf(1, 2)
            val taskSkills = arrayOf(1, 2, 3)

            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            val ex = result.shouldBeLeft()
            ex.shouldBeInstanceOf<IllegalSkillSets>()
            ex.message shouldBe "Skill set from employee (2) and task (3) are not equal."
        }

        test("empty arrays throw exception") {
            val employeeSkills = emptyArray<Int>()
            val taskSkills = emptyArray<Int>()

            val result = estimator.skillsEstimation(employeeSkills, taskSkills)
            val ex = result.shouldBeLeft()
            ex.shouldBeInstanceOf<IllegalNumSkills>()
            ex.message shouldBe "Insufficient employee skills 0, must be at least 2."
        }
    })
