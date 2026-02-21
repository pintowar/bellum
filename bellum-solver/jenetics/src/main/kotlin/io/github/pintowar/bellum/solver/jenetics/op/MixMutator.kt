package io.github.pintowar.bellum.solver.jenetics.op

import io.jenetics.Chromosome
import io.jenetics.Gene
import io.jenetics.Mutator
import io.jenetics.MutatorResult
import io.jenetics.util.ISeq
import java.util.random.RandomGenerator

class MixMutator<G : Gene<*, G>, C : Comparable<C>>(
    probability: Double = 0.2,
) : Mutator<G, C>(probability) {
    public override fun mutate(
        chromosome: Chromosome<G>,
        p: Double,
        random: RandomGenerator,
    ): MutatorResult<Chromosome<G>> =
        if (random.nextDouble() <= 0.3) {
            InverseMutator.inverseMutate(chromosome, p, random)
        } else {
            swapMutate(chromosome, p, random)
        }

    private fun swapMutate(
        chromosome: Chromosome<G>,
        p: Double,
        random: RandomGenerator,
    ): MutatorResult<Chromosome<G>> {
        val genes = chromosome.toMutableList()
        var mutations = 0
        for (i in genes.indices) {
            if (random.nextDouble() < p) {
                val j = random.nextInt(genes.size)
                val temp = genes[i]
                genes[i] = genes[j]
                genes[j] = temp
                mutations++
            }
        }
        return if (mutations > 0) {
            MutatorResult(chromosome.newInstance(ISeq.of(genes)), mutations)
        } else {
            MutatorResult(chromosome, 0)
        }
    }
}
