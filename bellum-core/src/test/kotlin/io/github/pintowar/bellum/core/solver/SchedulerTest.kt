package io.github.pintowar.bellum.core.solver

import io.github.pintowar.bellum.core.DataFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class SchedulerTest :
    FunSpec({

        test("findOptimalSchedule should not face a race condition when called concurrently") {
            // 1. Arrange
            val scheduler = spyk<Scheduler>()
            val dummyProject = DataFixtures.sampleProjectSmall

            // Mock the behavior of solveOptimizationProblem.
            // We add a delay to increase the chance of threads interleaving,
            // making the race condition more likely to appear if the code is flawed.
            coEvery { scheduler.solveOptimizationProblem(any(), any(), any(), any()) } coAnswers {
                delay(100) // Simulate work
                Result.success(SchedulerSolution(DataFixtures.sampleProjectSmall, false, 100.milliseconds))
            }

            val concurrentJobs = 100 // Number of concurrent calls to simulate

            // 2. Act
            // Launch all findOptimalSchedule calls concurrently and store their results.
            // coroutineScope will ensure all launched async jobs complete before continuing.
            val results =
                coroutineScope {
                    (1..concurrentJobs)
                        .map {
                            async { scheduler.findOptimalSchedule(dummyProject) }
                        }.awaitAll()
                }

            // 3. Assert
            val successfulResults = results.filter { it.isSuccess }
            val failedResults = results.filter { it.isFailure }

            // We expect exactly ONE successful call
            successfulResults.shouldHaveSize(1)
            successfulResults.first().shouldBeSuccess {
                it.project.name shouldBe "Sample Project Small"
            }

            // And all other calls must have failed
            failedResults.shouldHaveSize(concurrentJobs - 1)
            failedResults.forEach { result ->
                result.shouldBeFailure { exception ->
                    exception.shouldBeInstanceOf<IllegalStateException>()
                    exception.message shouldBe "Scheduler is already processing."
                }
            }

            // Crucially, verify that the actual heavy work was only performed ONCE.
            verify(exactly = 1) {
                scheduler.solveOptimizationProblem(any(), any(), any(), any())
            }
        }

        test("scheduler should be reusable after a findOptimalSchedule operation completes") {
            val scheduler = spyk<Scheduler>()
            val dummyProject = DataFixtures.sampleProjectSmall

            // First call should succeed
            scheduler.findOptimalSchedule(dummyProject).shouldBeSuccess()

            // The state should be reset to IDLE, so a second call should also succeed
            scheduler.findOptimalSchedule(dummyProject).shouldBeSuccess()
        }
    })
