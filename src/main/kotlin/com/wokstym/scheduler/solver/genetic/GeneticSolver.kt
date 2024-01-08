package com.wokstym.scheduler.solver.genetic

import arrow.core.Either
import arrow.core.right
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.overlappingSlotsPairs
import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import kotlin.time.measureTimedValue


class GeneticSolver(
    private val populationSize: Int = 300,
    private val eliteSize: Int = 30,
    private val maxGenerations: Long = 200L,
    private val mutationProbability: Double = 0.1,
    private val violationWeight: Int = 80,
    private val crossoverProbability: Double = 0.9,


    private val localSearchNeighboursCount: Int = 10,
    private val localSearchIterations: Int = 10,

    private val cacheFitness: Boolean = true
) : Solver {

    override val algorithm: Solver.Algorithm = Solver.Algorithm.GENETIC
    private val crossoverPoints = 2

    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {
        val overlaps = allExistingOverlaps(slots)
        val evaluator = GenotypeCacheEvaluator(overlaps, slots, students, violationWeight, cacheFitness)


        val starterGenotype = createStarterGenotype(students, slots)
        val localSearchParams = LocalSearchParams<BitGene, Int>(
            localSearchNeighboursCount,
            localSearchIterations
        ) { gen -> evaluator.evaluateFromGenotype(gen) }


        val engine = Engine
            .builder(
                { gen -> evaluator.evaluateFromGenotype(gen) },
                starterGenotype
            )
            .populationSize(populationSize)
            .selector(CustomEliteLocalSearchSelector(localSearchParams, eliteSize))
//            .selector(EliteSelector(eliteSize))
            .alterers(Mutator(mutationProbability), MultiPointCrossover(crossoverProbability, crossoverPoints))
            .build()

        val (result, timeTaken) = measureTimedValue {
            engine.stream()
                .limit(maxGenerations)
                .collect(EvolutionResult.toBestEvolutionResult())
        }

        val bestGenotype = result.bestPhenotype().genotype()
        val classesWithPeopleAssigned = evaluator.transformGenotype(bestGenotype)
        val (fitness, violations) = evaluator.evaluateWithViolations(
            classesWithPeopleAssigned,
            true
        )

        println("Found best genotype with firtness=$fitness")

        return SolverResult(
            classesWithPeopleAssigned,
            Statistics(
                timeTaken.inWholeMilliseconds / 1000.0,
                mapOf(
                    "Happiness" to calculateHappiness(classesWithPeopleAssigned).toString(),
                    "Generations" to result.generation().toString(),
                    "Fitness" to fitness.toString(),
                    "Violations" to violations.toString(),
                    )
            ),
            mapOf(
                "populationSize" to populationSize.toString(),
                "eliteSize" to eliteSize.toString(),
                "maxGenerations" to maxGenerations.toString(),
                "mutationProbability" to mutationProbability.toString(),
                "violationWeight" to violationWeight.toString(),
                "crossoverProbability" to crossoverProbability.toString(),
                "localSearchNeighboursCount" to localSearchNeighboursCount.toString(),
                "localSearchIterations" to localSearchIterations.toString(),
                "cacheFitness" to cacheFitness.toString(),
            )
        ).right()
    }


    private fun allExistingOverlaps(slots: List<ClassSlot>) =
        overlappingSlotsPairs(slots).groupBy(
            { it.first.id },
            { it.second.id })
            .mapValues { it.value.toSet() }

    private fun createStarterGenotype(students: List<Person>, slots: List<ClassSlot>): Genotype<BitGene> {
        // Random bits
        return Genotype.of(
            slots.map { BitChromosome.of(students.size, 0.5) }
        )
    }

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()


    }
}