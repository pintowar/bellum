package io.github.pintowar.bellum.solver.jenetics

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.estimator.TimeEstimator
import io.github.pintowar.bellum.core.solver.Scheduler
import io.github.pintowar.bellum.core.solver.SchedulerSolution
import io.github.pintowar.bellum.solver.jenetics.op.MixMutator
import io.jenetics.EnumGene
import io.jenetics.Optimize
import io.jenetics.PartiallyMatchedCrossover
import io.jenetics.engine.Codecs
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.EvolutionStatistics
import io.jenetics.engine.Limits
import io.jenetics.stat.DoubleMomentStatistics
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import java.time.Duration as JavaDuration

/**
 * Genetic algorithm-based scheduler implementation using Jenetics library.
 *
 * This scheduler uses a permutation-based genetic algorithm to optimize task assignments.
 * Each individual in the population represents a task ordering, which is then decoded
 * into a concrete schedule by greedily assigning tasks to the best available employees.
 *
 * The fitness function minimizes:
 * - Total project makespan (weighted heavily)
 * - Priority inversions (lower priority tasks scheduled before higher priority ones)
 * - Constraint violations (circular dependencies, missing skills)
 *
 * @param estimator The time estimator used to calculate task durations for employee-task pairs.
 */
class JeneticsScheduler(
    override val estimator: TimeEstimator,
) : Scheduler() {
    /**
     * Solves the project scheduling optimization problem using genetic algorithms.
     *
     * The algorithm evolves a population of task permutations over time, decoding each
     * permutation into a schedule and evaluating its fitness. Better solutions are
     * reported via the callback as they are discovered.
     *
     * @param project The project containing tasks and employees to schedule.
     * @param timeLimit Maximum duration for the optimization process.
     * @param numThreads Number of threads for parallel evaluation (currently unused in this implementation).
     * @param callback Function called with each improved solution found during evolution.
     * @return A [Result] containing the best [SchedulerSolution] found, or an error if scheduling failed.
     */
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val tasks = project.allTasks()
            val numTasks = tasks.size

            if (numTasks == 0) {
                return@runCatching SchedulerSolution(
                    project,
                    true,
                    0.minutes,
                    buildStatsMap(),
                )
            }

            val decoder = ScheduleDecoder(project, estimator)
            val engine = createEngine(project, decoder)
            val statistics = EvolutionStatistics.ofNumber<Long>()

            var bestFitness = Long.MAX_VALUE
            val initSolving = Clock.System.now()

            val evolutionResult =
                engine
                    .stream()
                    .limit(Limits.byExecutionTime(JavaDuration.ofMillis(timeLimit.inWholeMilliseconds)))
                    .peek(statistics)
                    .peek { evResult ->
                        val currentFitness = evResult.bestPhenotype().fitness()
                        if (currentFitness < bestFitness) {
                            bestFitness = currentFitness
                            val decoded = decoder.decode(extractPermutation(evResult.bestPhenotype().genotype()))
                            val sol = createSolution(decoded.project, currentFitness, evResult.generation(), statistics, initSolving)
                            callback(sol)
                        }
                    }.collect(EvolutionResult.toBestEvolutionResult())

            val decoded = decoder.decode(extractPermutation(evolutionResult.bestPhenotype().genotype()))
            val currentDuration = Clock.System.now() - initSolving
            val stats = buildStatsMap(decoded.fitness, evolutionResult.totalGenerations(), statistics)

            SchedulerSolution(decoded.project, false, currentDuration, stats)
        }

    /**
     * Creates the Jenetics evolution engine for the scheduling problem.
     *
     * The engine uses a permutation codec where each gene represents a task index.
     * The codec maps permutations to decoded schedules, allowing the fitness function
     * to operate directly on domain objects.
     *
     * @param project The project being optimized.
     * @param decoder The decoder used to convert permutations to schedules.
     * @return A configured [Engine] ready for evolution.
     */
    private fun createEngine(
        project: Project,
        decoder: ScheduleDecoder,
    ): Engine<EnumGene<Int>, Long> {
        val numTasks = project.allTasks().size

        val permutationCodec = Codecs.ofPermutation(numTasks)
        val codec = permutationCodec.map { arr -> decoder.decode(arr.toList()) }

        return Engine
            .builder({ decoded: ScheduleDecoder.DecodedSchedule -> decoded.fitness }, codec)
            .optimize(Optimize.MINIMUM)
            .populationSize(100)
            .alterers(
                PartiallyMatchedCrossover(0.8),
                MixMutator(0.1),
            ).build()
    }

    /**
     * Extracts a permutation list from a Jenetics genotype.
     *
     * @param genotype The genotype containing a permutation chromosome.
     * @return A list of integers representing the task ordering.
     */
    private fun extractPermutation(genotype: io.jenetics.Genotype<EnumGene<Int>>): List<Int> =
        genotype.chromosome().toMutableList().map { it.allele() }

    /**
     * Creates a scheduler solution from decoded scheduling results.
     *
     * @param project The scheduled project with assigned tasks.
     * @param fitness The fitness value of the solution.
     * @param generations The generation number when this solution was found.
     * @param statistics Evolution statistics collected during the run.
     * @param initSolving The timestamp when solving started.
     * @return A [SchedulerSolution] containing the results.
     */
    private fun createSolution(
        project: Project,
        fitness: Long,
        generations: Long,
        statistics: EvolutionStatistics<Long, DoubleMomentStatistics>,
        initSolving: Instant,
    ): SchedulerSolution {
        val currentDuration = Clock.System.now() - initSolving
        val stats = buildStatsMap(fitness, generations, statistics)
        return SchedulerSolution(project, false, currentDuration, stats)
    }

    /**
     * Builds a statistics map for reporting solver results.
     *
     * Contains fitness metrics, generation counts, and evolution statistics
     * useful for analyzing solver performance.
     *
     * @param fitness The final fitness value.
     * @param generations Total number of generations evolved.
     * @param statistics Evolution statistics collected during the run.
     * @return A map of statistic names to their values.
     */
    private fun buildStatsMap(
        fitness: Long = 0,
        generations: Long = 0,
        statistics: EvolutionStatistics<Long, DoubleMomentStatistics>? = null,
    ): Map<String, Any> {
        val fitnessStats = statistics?.fitness()
        return mapOf(
            "solver" to "Jenetics",
            "fitness" to fitness,
            "generations" to generations,
            "fitnessMin" to (fitnessStats?.min() ?: 0.0),
            "fitnessMax" to (fitnessStats?.max() ?: 0.0),
            "fitnessMean" to (fitnessStats?.mean() ?: 0.0),
            "fitnessVariance" to (fitnessStats?.variance() ?: 0.0),
            "alteredCount" to (statistics?.altered()?.sum() ?: 0L),
            "killedCount" to (statistics?.killed()?.sum() ?: 0L),
            "invalidCount" to (statistics?.invalids()?.sum() ?: 0L),
        )
    }
}
