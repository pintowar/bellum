package io.github.pintowar.bellum.solver.jenetics.op

import io.jenetics.Chromosome
import io.jenetics.Gene
import io.jenetics.Mutator
import io.jenetics.MutatorResult
import io.jenetics.util.ISeq
import java.util.random.RandomGenerator

class InverseMutator<G : Gene<*, G>, C : Comparable<C>>(
    probability: Double = 0.2,
) : Mutator<G, C>(probability) {
    public override fun mutate(
        chromosome: Chromosome<G>,
        p: Double,
        random: RandomGenerator,
    ): MutatorResult<Chromosome<G>> = inverseMutate(chromosome, p, random)

    companion object {
        fun <G : Gene<*, G>> inverseMutate(
            chromosome: Chromosome<G>,
            p: Double,
            random: RandomGenerator,
        ): MutatorResult<Chromosome<G>> {
            if (chromosome.length() > 2 && random.nextDouble() <= p) {
                val genes = chromosome.toMutableList()
                var start = random.nextInt(genes.size)
                var end = random.nextInt(genes.size)
                while (start == end) {
                    end = random.nextInt(genes.size)
                }
                if (start > end) {
                    val temp = start
                    start = end
                    end = temp
                }

                val hlf = (end - start + 1) / 2
                for (i in 0 until hlf) {
                    val temp = genes[start + i]
                    genes[start + i] = genes[end - i]
                    genes[end - i] = temp
                }
                return MutatorResult(chromosome.newInstance(ISeq.of(genes)), hlf)
            }
            return MutatorResult(chromosome, 0)
        }
    }
}
