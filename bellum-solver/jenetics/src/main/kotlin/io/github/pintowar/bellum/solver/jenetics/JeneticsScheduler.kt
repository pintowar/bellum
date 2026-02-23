package io.github.pintowar.bellum.solver.jenetics

import io.github.pintowar.bellum.core.domain.AssignedTask
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
import java.time.Duration as JavaDuration

class JeneticsScheduler(
    override val estimator: TimeEstimator,
) : Scheduler() {
    override fun solveOptimizationProblem(
        project: Project,
        timeLimit: Duration,
        numThreads: Int,
        callback: (SchedulerSolution) -> Unit,
    ): Result<SchedulerSolution> =
        runCatching {
            val tasks = project.allTasks()
            val numTasks = tasks.size

            if (numTasks == 0) return@runCatching SchedulerSolution(
                project,
                true,
                0.minutes,
                mapOf("solver" to "Jenetics Solver")
            )

            val engine = createEngine(project)
            val statistics = EvolutionStatistics.ofNumber<Long>()

            var bestFitness = Long.MAX_VALUE
            val initSolving = Clock.System.now()
            val evolutionResult =
                engine
                    .stream()
                    .limit(Limits.byExecutionTime(JavaDuration.ofMillis(timeLimit.inWholeMilliseconds)))
                    .peek(statistics)
                    .peek { evResult ->
                        val phenotype = evResult.bestPhenotype()
                        val currentFitness = phenotype.fitness()
                        if (currentFitness < bestFitness) {
                            bestFitness = currentFitness
                            val (newProject, _) =
                                decodeSchedule(
                                    phenotype
                                        .genotype()
                                        .chromosome()
                                        .toMutableList()
                                        .map { it.allele() },
                                    project,
                                )
                            val sol =
                                SchedulerSolution(
                                    newProject,
                                    false,
                                    Clock.System.now() - initSolving,
                                    buildStatsMap(currentFitness, evResult.generation(), statistics),
                                )
                            callback(sol)
                        }
                    }.collect(EvolutionResult.toBestEvolutionResult())

            val (bestProject, fitness) =
                decodeSchedule(
                    evolutionResult
                        .bestPhenotype()
                        .genotype()
                        .chromosome()
                        .toMutableList()
                        .map { it.allele() },
                    project,
                )
            val currentDuration = Clock.System.now() - initSolving
            val stats = buildStatsMap(fitness, evolutionResult.totalGenerations(), statistics)
            SchedulerSolution(bestProject, false, currentDuration, stats)
        }

    private fun createEngine(project: Project): Engine<EnumGene<Int>, Long> {
        val numTasks = project.allTasks().size
        val evalFunc = { seq: IntArray ->
            val (_, fitness) = decodeSchedule(seq.toList(), project)
            fitness
        }

        return Engine
            .builder(evalFunc, Codecs.ofPermutation(numTasks))
            .optimize(Optimize.MINIMUM)
            .populationSize(100)
            .alterers(
                PartiallyMatchedCrossover(0.8),
                MixMutator(0.1),
            ).build()
    }

    private fun buildStatsMap(
        fitness: Long,
        generations: Long,
        statistics: EvolutionStatistics<Long, DoubleMomentStatistics>,
    ): Map<String, Any> {
        val fitnessStats = statistics.fitness()
        return mapOf(
            "solver" to "Jenetics",
            "fitness" to fitness,
            "generations" to generations,
            "fitnessMin" to fitnessStats.min(),
            "fitnessMax" to fitnessStats.max(),
            "fitnessMean" to fitnessStats.mean(),
            "fitnessVariance" to fitnessStats.variance(),
            "alteredCount" to statistics.altered().sum(),
            "killedCount" to statistics.killed().sum(),
            "invalidCount" to statistics.invalids().sum(),
        )
    }

    private fun decodeSchedule(
        permutation: List<Int>,
        project: Project,
    ): Pair<Project, Long> {
        val tasks = project.allTasks()
        val numTasks = tasks.size
        val employees = project.allEmployees()

        val rank = IntArray(numTasks)
        for (i in 0 until numTasks) {
            rank[permutation[i]] = i
        }

        val starts = IntArray(numTasks) { -1 }
        val durs = IntArray(numTasks) { 0 }
        val emps = IntArray(numTasks) { -1 }

        val unscheduled = mutableSetOf<Int>()
        val timelines = Array(employees.size) { mutableListOf<Pair<Int, Int>>() }

        fun findFreeTime(
            empIdx: Int,
            readyTime: Int,
            dur: Int,
        ): Int {
            val intervals = timelines[empIdx]
            var t = readyTime
            for (interval in intervals) {
                if (t + dur <= interval.first) return t
                if (t < interval.second) t = interval.second
            }
            return t
        }

        for (i in 0 until numTasks) {
            val t = tasks[i]
            if (t is AssignedTask && t.pinned) {
                val empIdx = employees.indexOfFirst { it.id == t.employee.id }.takeIf { it >= 0 } ?: 0
                val startOff = (t.startAt - project.kickOff).inWholeMinutes.toInt()
                val dur = t.duration.inWholeMinutes.toInt()
                starts[i] = startOff
                durs[i] = dur
                emps[i] = empIdx
                timelines[empIdx].add(startOff to startOff + dur)
                timelines[empIdx].sortBy { it.first }
            } else {
                unscheduled.add(i)
            }
        }

        val taskDeps =
            IntArray(numTasks) { i ->
                val depId = tasks[i].dependsOn?.id
                if (depId != null) tasks.indexOfFirst { it.id == depId } else -1
            }

        var penalty = 0L

        while (unscheduled.isNotEmpty()) {
            val available =
                unscheduled.filter { i ->
                    val dep = taskDeps[i]
                    dep == -1 || !unscheduled.contains(dep)
                }

            if (available.isEmpty()) {
                // circular dependency or failure, break and penalize heavily
                penalty += 10_000_000L
                for (i in unscheduled) {
                    starts[i] = 1000000
                    durs[i] = 0
                    emps[i] = 0
                }
                unscheduled.clear()
                break
            }

            val chosen = available.minByOrNull { rank[it] }!!
            val dep = taskDeps[chosen]
            val readyTime = if (dep != -1) starts[dep] + durs[dep] else 0

            var bestEmpIdx = -1
            var bestStart = Int.MAX_VALUE
            var bestFinish = Int.MAX_VALUE
            var bestDur = 0

            // If task was already assigned in a partial project (and not pinned), try to preserve it by giving it a strong preference
            val assignedEmployeeId =
                if (tasks[chosen] is AssignedTask) (tasks[chosen] as AssignedTask).employee.id else null

            for (e in employees.indices) {
                val durResult = estimator.estimate(employees[e], tasks[chosen])
                if (durResult.isSuccess) {
                    val d = durResult.getOrThrow().inWholeMinutes.toInt()
                    val s = findFreeTime(e, readyTime, d)
                    val finish = s + d

                    // Give priority to the already assigned employee ONLY if finishing time is equal
                    val isAssignedEmp = assignedEmployeeId == employees[e].id

                    if (bestEmpIdx == -1 || finish < bestFinish || (finish == bestFinish && isAssignedEmp)) {
                        bestEmpIdx = e
                        bestStart = s
                        bestFinish = finish
                        bestDur = d
                    }
                }
            }

            if (bestEmpIdx == -1) {
                // No employee has skills
                penalty += 1_000_000L
                bestEmpIdx = 0
                bestStart = readyTime
                bestDur = 10
            }

            starts[chosen] = bestStart
            durs[chosen] = bestDur
            emps[chosen] = bestEmpIdx

            timelines[bestEmpIdx].add(bestStart to bestStart + bestDur)
            timelines[bestEmpIdx].sortBy { it.first }
            unscheduled.remove(chosen)
        }

        val makespan = (0 until numTasks).maxOfOrNull { starts[it] + durs[it] } ?: 0

        var priorityCost = 0L
        for (i in 0 until numTasks) {
            for (j in i + 1 until numTasks) {
                val p1 = tasks[i].priority.value
                val p2 = tasks[j].priority.value
                // Assuming priority.value is 0 for critical, 1 for major, 2 for minor (lower is more critical)
                // We penalize if a more critical task starts AFTER a less critical task
                if (p1 > p2 && starts[i] < starts[j]) priorityCost++
                if (p2 > p1 && starts[j] < starts[i]) priorityCost++
            }
        }

        val totalFitness = penalty + makespan * 100L + priorityCost

        val assignedTasks =
            tasks
                .mapIndexed { idx, t ->
                    t
                        .assign(
                            employees[emps[idx]],
                            project.kickOff + starts[idx].minutes,
                            durs[idx].minutes,
                        ).let {
                            if (t is AssignedTask && t.pinned) (it as AssignedTask).pin() else it
                        }
                }.toSet()

        val newProject = project.replace(tasks = assignedTasks).getOrThrow()

        return Pair(newProject, totalFitness)
    }
}
