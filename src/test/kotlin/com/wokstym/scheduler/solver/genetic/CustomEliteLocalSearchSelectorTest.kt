package com.wokstym.scheduler.solver.genetic

import io.jenetics.*
import io.jenetics.util.ISeq
import io.jenetics.util.MSeq
import org.junit.jupiter.api.Test

import java.util.random.RandomGenerator

class CustomEliteLocalSearchSelectorTest {

    private fun randomGenotype(): Genotype<BitGene> {
        return Genotype.of(
            (0..3).map { BitChromosome.of(4, 0.5) }
        )
    }


    @Test
    fun select() {
        val xd = Phenotype.of(randomGenotype(), 0, 1)
//        val mutator: Mutator<BitGene, Int> = SwapMutator<BitGene, Int>(1.0 / xd.genotype().length())


        println(xd.genotype())
        println(swapGenes(xd))


//        val dd = mutator.alter(
//            ISeq.of(xd), 1
//        )
//
//        println(dd.population[0].genotype())
//        println(dd.population)


//
//        val population = ISeq.of(1, 10, 100, 150)
//            .map { Phenotype.of(randomGenotype(), 0, it) }
//
//        val selector = CustomEliteLocalSearchSelector<BitGene, Int>({ 0 }, 1)
//
//        val result = selector.select(population, 4, Optimize.MAXIMUM)
//
//        println(population)
//        println(result)


    }

    private fun swapGenes(xd: Phenotype<BitGene, Int>): Phenotype<BitGene, Int> {
        val genotype = xd.genotype()
        val genotypeList = genotype.toMutableList()
        if (genotype.length() < 2)
            return xd

        val (index1, index2) = RandomGenerator.getDefault()
            .ints(0, genotype.length())
            .distinct()
            .limit(2)
            .toArray()


        val first = genotype[index1]
        val second = genotype[index2]

        val firstGenes = MSeq.of(first)
        val secondGenes = MSeq.of(second)

        val position = RandomGenerator.getDefault().nextInt(first.length())

        val old = firstGenes[position]
        firstGenes[position] = secondGenes[position]
        secondGenes[position] = old


        val newFirst = first.newInstance(firstGenes.toISeq())
        val newSecond = second.newInstance(secondGenes.toISeq())

        genotypeList[index1] = newFirst
        genotypeList[index2] = newSecond

        val newGenotype = Genotype.of(genotypeList)
        return Phenotype.of(newGenotype, xd.generation())
    }
}