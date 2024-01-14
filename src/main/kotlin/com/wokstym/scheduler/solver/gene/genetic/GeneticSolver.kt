package com.wokstym.scheduler.solver.gene.genetic

import arrow.core.Either
import arrow.core.right
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.allExistingOverlaps
import com.wokstym.scheduler.solver.gene.common.CustomSwapMutator
import com.wokstym.scheduler.solver.gene.common.GenotypeCacheEvaluator
import com.wokstym.scheduler.solver.gene.common.createRandomStarterGenotype
import com.wokstym.scheduler.solver.gene.common.filterLectures
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import kotlin.time.measureTimedValue


class GeneticSolver(
    private val populationSize: Int = 400,
    private val eliteSize: Int = 6,
    private val maxGenerations: Long = 1000L,
    private val mutationProbability: Double = 0.9,
    private val violationWeight: Int = 80,
    private val crossoverProbability: Double = 0.1,

    private val localSearchNeighboursCount: Int = 20,
    private val localSearchIterations: Int = 40,

    private val cacheFitness: Boolean = true
) : Solver {

    override val algorithm: Solver.Algorithm = Solver.Algorithm.GENETIC

    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {
        val (studentsWithoutLectures, slotsWithoutLectures) = filterLectures(students, slots)

        val overlaps = allExistingOverlaps(slotsWithoutLectures)
        val evaluator = GenotypeCacheEvaluator(
            overlaps,
            slotsWithoutLectures,
            studentsWithoutLectures,
            violationWeight,
            cacheFitness
        )


        val starterGenotype = createRandomStarterGenotype(studentsWithoutLectures, slotsWithoutLectures)
        val localSearchParams = LocalSearchParams(
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
            .alterers(CustomSwapMutator(0.5, mutationProbability))
            .build()

        val (result, timeTaken) = measureTimedValue {
            engine.stream()
                .peek{
                    println(it.generation() to it.bestFitness())
                }
                .limit(maxGenerations)
                .collect(EvolutionResult.toBestEvolutionResult())
        }

        val bestGenotype = result.bestPhenotype().genotype()
        val classesWithPeopleAssigned = evaluator.transformGenotype(bestGenotype)
        val (fitness, violations) = evaluator.evaluateWithViolations(
            classesWithPeopleAssigned,
            true
        )

        println("Found best genotype with fitness=$fitness")

        return SolverResult(
            evaluator.addLectures(classesWithPeopleAssigned, slots),
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

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()


    }
}