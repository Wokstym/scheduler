package com.wokstym.scheduler.solver.annealing

import io.jenetics.*
import io.jenetics.internal.math.Probabilities
import io.jenetics.util.MSeq
import java.util.random.RandomGenerator
import kotlin.random.Random

// TODO gotowe, ale czy potrzebne? XD
class CustomSwapMutator< C : Comparable<C>>(
    probabilityOf: Double = DEFAULT_ALTER_PROBABILITY
) : Mutator<BitGene, C>(probabilityOf) {


    override fun mutate(
        genotype: Genotype<BitGene>,
        p: Double,
        random: RandomGenerator
    ): MutatorResult<Genotype<BitGene>> {
        if (genotype.length() == 0) {
            return MutatorResult(genotype, 0)
        }

        val genotypeList = genotype.toMutableList()
        val firstGenotypeIndex = Random.nextInt(genotype.length())

        val newFirst = flipGenes(genotype[firstGenotypeIndex])

        genotypeList[firstGenotypeIndex] = newFirst

        return MutatorResult(
            Genotype.of(genotypeList),
            1
        )
    }

    private fun flipGenes(
        firstGenotype: Chromosome<BitGene>,
    ): Chromosome<BitGene> {
        val firstGenes = MSeq.of(firstGenotype)
        val position = Random.nextInt(firstGenotype.length())

        firstGenes[position] =  firstGenes[position].flip()
        return firstGenotype.newInstance(firstGenes.toISeq())
    }

    private fun BitGene.flip(): BitGene {
        return this.newInstance(!this.booleanValue())
    }

    private fun get2RandomIndexes(genotype: Genotype<BitGene>): IntArray = RandomGenerator.getDefault()
        .ints(0, genotype.length())
        .distinct()
        .limit(2)
        .toArray()
}

