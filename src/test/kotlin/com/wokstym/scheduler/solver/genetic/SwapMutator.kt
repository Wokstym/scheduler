package com.wokstym.scheduler.solver.genetic

import io.jenetics.*
import io.jenetics.internal.math.Probabilities
import io.jenetics.util.MSeq
import java.util.random.RandomGenerator

// TODO gotowe, ale czy potrzebne? XD
class SwapMutator<G : Gene<*, G>, C : Comparable<C>>(
    probability: Double = DEFAULT_ALTER_PROBABILITY
) : Mutator<G, C>(probability) {


    override fun mutate(
        genotype: Genotype<G>,
        p: Double,
        random: RandomGenerator
    ): MutatorResult<Genotype<G>> {
        if (genotype.length() < 2 && random.nextInt() < Probabilities.toInt(p)) {
            return MutatorResult(genotype, 0)
        }

        val genotypeList = genotype.toMutableList()
        val (firstGenotypeIndex, secondGenotypeIndex) = get2RandomIndexes(genotype)


        val (newFirst, newSecond) = swapGenes(
            genotype[firstGenotypeIndex],
            genotype[secondGenotypeIndex]
        )

        genotypeList[firstGenotypeIndex] = newFirst
        genotypeList[secondGenotypeIndex] = newSecond

        return MutatorResult(
            Genotype.of(genotypeList),
            1
        )
    }

    private fun swapGenes(
        firstGenotype: Chromosome<G>,
        secondGenotype: Chromosome<G>
    ): Pair<Chromosome<G>, Chromosome<G>> {
        val firstGenes = MSeq.of(firstGenotype)
        val secondGenes = MSeq.of(secondGenotype)

        val position = RandomGenerator.getDefault().nextInt(firstGenotype.length())

        val old = firstGenes[position]
        firstGenes[position] = secondGenes[position]
        secondGenes[position] = old


        return Pair(
            firstGenotype.newInstance(firstGenes.toISeq()),
            secondGenotype.newInstance(secondGenes.toISeq())
        )
    }

    private fun get2RandomIndexes(genotype: Genotype<G>): IntArray = RandomGenerator.getDefault()
        .ints(0, genotype.length())
        .distinct()
        .limit(2)
        .toArray()
}

