import io.github.pintowar.rts.core.domain.Employee
import io.github.pintowar.rts.core.domain.EmployeeId
import io.github.pintowar.rts.core.domain.Project
import io.github.pintowar.rts.core.domain.Task
import io.github.pintowar.rts.core.domain.TaskId
import io.github.pintowar.rts.core.domain.UnassignedTask
import io.github.pintowar.rts.core.estimator.EstimationMatrix
import io.github.pintowar.rts.core.estimator.TimeEstimator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration

class EstimationMatrixTest :
    FunSpec({
        test("create matrix from project") {
            // Setup
            val employee = Employee.valueOf("Alice").getOrNull()!!
            val task = UnassignedTask.valueOf("Task 1").getOrNull()!!
            val project = Project.valueOf(employees = setOf(employee), tasks = setOf(task)).getOrThrow()
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns Duration.ofHours(1)

            val matrix = EstimationMatrix.valueOf(project, estimator)

            // Test valid duration
            val result = matrix.duration(employee.id, task.id)
            result shouldBeSuccess Duration.ofHours(1)
        }

        test("cache duration after first estimation") {
            // Setup
            val employee = Employee.valueOf("Alice").getOrNull()!!
            val task = UnassignedTask.valueOf("Task 1").getOrNull()!!
            val project = Project.valueOf(employees = setOf(employee), tasks = setOf(task)).getOrThrow()
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns Duration.ofHours(1)
            val matrix = EstimationMatrix.valueOf(project, estimator)

            // First call
            val result1 = matrix.duration(employee.id, task.id)
            result1 shouldBeSuccess Duration.ofHours(1)
            verify(exactly = 1) { estimator.estimate(employee, task) }

            // Second call
            val result2 = matrix.duration(employee.id, task.id)
            result2 shouldBeSuccess Duration.ofHours(1)
            verify(exactly = 1) { estimator.estimate(employee, task) }
        }

        test("return InvalidEmployeeId when employee not found") {
            // Setup
            val employee = Employee.valueOf("Alice").getOrNull()!!
            val task = UnassignedTask.valueOf("Task 1").getOrNull()!!
            val project = Project.valueOf(employees = setOf(employee), tasks = setOf(task)).getOrThrow()
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns Duration.ofHours(1)

            val matrix = EstimationMatrix.valueOf(project, estimator)
            val invalidEmployeeId = EmployeeId()

            // Test
            val result = matrix.duration(invalidEmployeeId, task.id)
            result.shouldBeFailure<IllegalArgumentException>()
        }

        test("return InvalidTaskId when task not found") {
            // Setup
            val employee = Employee.valueOf("Alice").getOrNull()!!
            val task = UnassignedTask.valueOf("Task 1").getOrNull()!!
            val project = Project.valueOf(employees = setOf(employee), tasks = setOf(task)).getOrThrow()
            val estimator = mockk<TimeEstimator>()
            every { estimator.estimate(any<Employee>(), any<Task>()) } returns Duration.ofHours(1)

            val matrix = EstimationMatrix.valueOf(project, estimator)
            val invalidTaskId = TaskId()

            // Test
            val result = matrix.duration(employee.id, invalidTaskId)
            result.shouldBeFailure<IllegalArgumentException>()
        }
    })
