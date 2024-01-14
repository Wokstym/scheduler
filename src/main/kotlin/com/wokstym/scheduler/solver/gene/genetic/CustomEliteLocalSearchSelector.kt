package com.wokstym.scheduler.solver.gene.genetic

import com.wokstym.scheduler.solver.gene.common.CustomSwapMutator
import io.jenetics.*
import io.jenetics.util.ISeq
import io.jenetics.util.Seq
import kotlin.math.max
import kotlin.math.min


data class LocalSearchParams<C : Comparable<C>>(
    val neighboursCount: Int,
    val searchIterations: Int,
    val evaluate: (gen: Genotype<BitGene>) -> C
)

/**
 * This is a modification of [io.jenetics.EliteSelector] to perform elite local search on copied elite population.
 */
class CustomEliteLocalSearchSelector<C : Comparable<C>>(
    private val localSearchParams: LocalSearchParams<C>,
    private val eliteCount: Int = 1,
    private val nonEliteSelector: Selector<BitGene, C> = TournamentSelector(3)
) : Selector<BitGene, C> {

    private val eliteSelector = TruncationSelector<BitGene, C>()
    private val mutator = CustomSwapMutator<C>()


    private fun ISeq<Phenotype<BitGene, C>>.mutate(generation: Long): ISeq<Phenotype<BitGene, C>> {
        return mutator.alter(this, generation).population
    }

    private fun localSearch(initial: Phenotype<BitGene, C>): Phenotype<BitGene, C> {
        val generation = initial.generation()
        var bestSolution = initial

        for(i in 0..<localSearchParams.searchIterations) {

            val neighbourBest = ISeq.of(0..<localSearchParams.neighboursCount)
                .map { bestSolution }
                .mutate(generation)
                .map { ph -> ph.eval { (localSearchParams.evaluate(it)) } }
                .maxBy { it.fitness() }

            if (bestSolution.fitness() < neighbourBest.fitness()) {
                if(bestSolution.fitness() as Int> 0L) {
                    println("$bestSolution\n$neighbourBest\n================")
                }
                bestSolution = neighbourBest
            }
        }

        return bestSolution
    }


    override fun select(
        population: Seq<Phenotype<BitGene, C>>,
        count: Int,
        opt: Optimize?
    ): ISeq<Phenotype<BitGene, C>> {
        require(count >= 0) {
            "Selection count must be greater or equal then zero, but was $count."
        }
        if (population.isEmpty || count <= 0) {
            return ISeq.empty()
        }

        val ec = min(count.toDouble(), eliteCount.toDouble()).toInt()
        val elitePopulation = eliteSelector.select(population, ec, opt)
            .map {
                val x = localSearch(it)
                x
            }

        // copied from EliteSelector
        val nonEliteRest = nonEliteSelector.select(population, max(0.0, (count - ec).toDouble()).toInt(), opt)


        return ISeq.of(nonEliteRest + elitePopulation)

    }

    override fun toString(): String {
        return String.format("CustomEliteLocalSearchSelector[%d]", eliteCount)
    }
}

