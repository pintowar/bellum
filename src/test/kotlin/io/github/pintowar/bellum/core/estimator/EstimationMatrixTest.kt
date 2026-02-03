package io.github.pintowar.bellum.core.estimator

import arrow.core.getOrElse
import arrow.core.right
import io.github.pintowar.bellum.core.DataFixtures.sampleProjectSmall
import io.github.pintowar.bellum.core.domain.Employee
import io.github.pintowar.bellum.core.domain.EmployeeId
import io.github.pintowar.bellum.core.domain.Task
import io.github.pintowar.bellum.core.domain.TaskId
import io.github.pintowar.bellum.core.domain.UnassignedTask
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.hours

class EstimationMatrixTest :
    FunSpec({
        test("create matrix from project") {
            // Setup
            val employee = Employee("Alice").getOrElse { throw it }
            val task = UnassignedTask("Task 1").getOrElse { throw it }
            val project = sampleProjectSmall.replace(employees = setOf(employee), tasks = setOf(task)).getOrElse { throw it }
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns 1.hours.right()

            val matrix = EstimationMatrix(project, estimator)

            // Test valid duration
            val result = matrix.duration(employee.id, task.id)
            result.shouldBeRight(1.hours)
        }

        test("cache duration after first estimation") {
            // Setup
            val employee = Employee("Alice").getOrElse { throw it }
            val task = UnassignedTask("Task 1").getOrElse { throw it }
            val project = sampleProjectSmall.replace(employees = setOf(employee), tasks = setOf(task)).getOrElse { throw it }
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns 1.hours.right()
            val matrix = EstimationMatrix(project, estimator)

            // First call
            val result1 = matrix.duration(employee.id, task.id)
            result1.shouldBeRight(1.hours)
            verify(exactly = 1) { estimator.estimate(employee, task) }

            // Second call
            val result2 = matrix.duration(employee.id, task.id)
            result2.shouldBeRight(1.hours)
            verify(exactly = 1) { estimator.estimate(employee, task) }
        }

        test("return InvalidEmployeeId when employee not found") {
            // Setup
            val employee = Employee("Alice").getOrElse { throw it }
            val task = UnassignedTask("Task 1").getOrElse { throw it }
            val project = sampleProjectSmall.replace(employees = setOf(employee), tasks = setOf(task)).getOrElse { throw it }
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns 1.hours.right()

            val matrix = EstimationMatrix(project, estimator)
            val invalidEmployeeId = EmployeeId()

            // Test
            val result = matrix.duration(invalidEmployeeId, task.id)
            val ex = result.shouldBeLeft()
            ex.shouldBeInstanceOf<IllegalArgumentException>()
        }

        test("return InvalidTaskId when task not found") {
            // Setup
            val employee = Employee("Alice").getOrElse { throw it }
            val task = UnassignedTask("Task 1").getOrElse { throw it }
            val project = sampleProjectSmall.replace(employees = setOf(employee), tasks = setOf(task)).getOrElse { throw it }
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns 1.hours.right()

            val matrix = EstimationMatrix(project, estimator)
            val invalidTaskId = TaskId()

            // Test
            val result = matrix.duration(employee.id, invalidTaskId)
            val ex = result.shouldBeLeft()
            ex.shouldBeInstanceOf<IllegalArgumentException>()
        }
    })
