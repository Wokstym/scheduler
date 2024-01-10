package com.wokstym.scheduler.solver.gene.annealing

import io.jenetics.*
import io.jenetics.util.MSeq
import java.util.random.RandomGenerator
import kotlin.random.Random

class CustomSwapMutator<C : Comparable<C>>(
    // How likely we are to bit flip or swap bits between classes
    private val probabilityOfBitFlip: Double = 0.5,
    mutationProbability: Double = DEFAULT_ALTER_PROBABILITY
) : Mutator<BitGene, C>(mutationProbability) {


    override fun mutate(
        genotype: Genotype<BitGene>,
        p: Double,
        random: RandomGenerator
    ): MutatorResult<Genotype<BitGene>> {
        if (genotype.length() == 0) {
            return MutatorResult(genotype, 0)
        }

        val genotypeList = if (random.nextDouble() < probabilityOfBitFlip) {
            bitFlipRandomGene(genotype)
        } else {
            swapGenesForStudent(genotype)
        }

        return MutatorResult(
            Genotype.of(genotypeList),
            1
        )
    }

    private fun swapGenesForStudent(genotype: Genotype<BitGene>): MutableList<Chromosome<BitGene>> {
        val genotypeList = genotype.toMutableList()
        val (firstGenotypeIndex, secondGenotypeIndex) = get2RandomIndexes(genotype)

        val (newFirst, newSecond) = swapGenes(
            genotype[firstGenotypeIndex],
            genotype[secondGenotypeIndex]
        )

        genotypeList[firstGenotypeIndex] = newFirst
        genotypeList[secondGenotypeIndex] = newSecond

        return genotypeList
    }

    private fun bitFlipRandomGene(genotype: Genotype<BitGene>): MutableList<Chromosome<BitGene>> {
        val genotypeList = genotype.toMutableList()
        val firstGenotypeIndex = Random.nextInt(genotype.length())

        val newFirst = flipGenes(genotype[firstGenotypeIndex])

        genotypeList[firstGenotypeIndex] = newFirst
        return genotypeList
    }

    private fun flipGenes(
        firstGenotype: Chromosome<BitGene>,
    ): Chromosome<BitGene> {
        val firstGenes = MSeq.of(firstGenotype)
        val position = Random.nextInt(firstGenotype.length())

        firstGenes[position] = firstGenes[position].flip()
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

    private fun swapGenes(
        firstGenotype: Chromosome<BitGene>,
        secondGenotype: Chromosome<BitGene>
    ): Pair<Chromosome<BitGene>, Chromosome<BitGene>> {
        if (firstGenotype == secondGenotype) {
            return firstGenotype to secondGenotype
        }

        val firstGenes = MSeq.of(firstGenotype)
        val secondGenes = MSeq.of(secondGenotype)

        val differentIndexes = (0..<firstGenes.length()).filter { firstGenes[it] != secondGenes[it] }

        val position = differentIndexes[RandomGenerator.getDefault().nextInt(differentIndexes.size)]

        val old = firstGenes[position]
        firstGenes[position] = secondGenes[position]
        secondGenes[position] = old


        return Pair(
            firstGenotype.newInstance(firstGenes.toISeq()),
            secondGenotype.newInstance(secondGenes.toISeq())
        )
    }
}

