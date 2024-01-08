package com.wokstym.scheduler.solver.genetic

import io.jenetics.*
import io.jenetics.util.ISeq
import io.jenetics.util.Seq
import kotlin.math.max
import kotlin.math.min


data class LocalSearchParams<G : Gene<*, G>, C : Comparable<C>>(
    val neighboursCount: Int,
    val searchIterations: Int,
    val evaluate: (gen: Genotype<G>) -> C
)

/**
 * This is a modification of [io.jenetics.EliteSelector] to perform elite local search on copied elite population.
 */
class CustomEliteLocalSearchSelector<G : Gene<*, G>, C : Comparable<C>>(
    private val localSearchParams: LocalSearchParams<G, C>,
    private val eliteCount: Int = 1,
    private val nonEliteSelector: Selector<G, C> = TournamentSelector(3)
) : Selector<G, C> {

    private val ELITE_SELECTOR = TruncationSelector<G, C>()
    private val mutator = SwapMutator<G, C>()


    private fun ISeq<Phenotype<G,C>>.mutate(generation: Long): ISeq<Phenotype<G, C>> {
        return mutator.alter(this, generation).population
    }

    private fun localSearch(initial: Phenotype<G, C>): Phenotype<G, C> {
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
        population: Seq<Phenotype<G, C>>,
        count: Int,
        opt: Optimize?
    ): ISeq<Phenotype<G, C>> {
        require(count >= 0) {
            "Selection count must be greater or equal then zero, but was $count."
        }
        if (population.isEmpty || count <= 0) {
            return ISeq.empty()
        }

        val ec = min(count.toDouble(), eliteCount.toDouble()).toInt()
        val elitePopulation = ELITE_SELECTOR.select(population, ec, opt)
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

