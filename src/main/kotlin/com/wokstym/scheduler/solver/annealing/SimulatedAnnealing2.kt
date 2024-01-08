package com.wokstym.scheduler.solver.annealing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.genetic.GenotypeCacheEvaluator
import com.wokstym.scheduler.solver.overlappingSlotsPairs
import io.jenetics.*
import io.jenetics.util.ISeq
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class SimulatedAnnealing2(

    private val logarithmicRate: Double = 0.5,
    private val exponentialRate: Double = 0.99,
    private val logarithmicProbability: Double = 0.3,
    private val iterations: Long = 100_000L
) : Solver {
    override val algorithm = Solver.Algorithm.SA

    private val mutator = CustomSwapMutator<Int>(1.0)

    private fun createStarterGenotype(students: List<Person>, slots: List<ClassSlot>): Genotype<BitGene> {
        // Random bits
        return Genotype.of(
            slots.map { BitChromosome.of(students.size, 0.5) }
        )
    }

    private fun mutate(
        evaluator: GenotypeCacheEvaluator,
        value: Phenotype<BitGene, Int>,
        currentGeneration: Long
    ): Phenotype<BitGene, Int> {
        return mutator.alter(ISeq.of(value), currentGeneration)
            .population[0]
            .eval { evaluator.evaluateFromGenotype(it) }
    }

    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {
        val overlaps = allExistingOverlaps(slots)
        val evaluator = GenotypeCacheEvaluator(overlaps, slots, students, 80, false)

        var S = createStarterGenotype(students, slots)
        var C = evaluator.evaluateFromGenotype(S)
        var CWithS = Phenotype.of(S, 0L, C)
        var temp = calculateInitialTemperature(S) {
            evaluator.evaluateFromGenotype(it)
        } // 2000

        var bestS = S
        var bestC = C

        var freezecount = 0

        val minpercent = 0.2
        val freezeLimit = 10
        val sizefactor = 10

        val fastfactor = 0.5 // 0.5
        val tempfactor = 0.98 // 0.98
        val tcent = 100

        val N = slots.size * students.size

        var iterations = 0

        val time = measureTime {
        while (freezecount < freezeLimit) {
            var changes = 0
            var trials = 0

            while (trials < sizefactor * N) {
                iterations+=1
                trials += 1

                val neighbour = mutate(evaluator, CWithS, 1)
                val neighbourCost = evaluator.evaluateFromGenotype(neighbour.genotype())
                val diff =  C - neighbourCost
                if (neighbourCost > bestC) {
                    bestC = neighbourCost
                    bestS = neighbour.genotype()
                }

                if (diff <= 0) {
                    changes += 1
                    C = neighbourCost
                    S = neighbour.genotype()
                    CWithS = Phenotype.of(S, 0L, C)

                } else {
                    val r = Random.nextDouble()

                    if (r <= exp(-(diff) / temp)) {
                        changes += 1
                        C = neighbourCost
                        S = neighbour.genotype()
                        CWithS = Phenotype.of(S, 0L, C)


                    }
                }

            }

            // fast reduction
            if (changes / trials > tcent)
                temp *= fastfactor
            // slow reduction
            else
                temp *= tempfactor;


            println( "$temp $bestC $C $freezecount ${changes.toDouble() / trials}"  )

            if (changes.toDouble() / trials < minpercent)
                freezecount += 1;
            else
                freezecount = 0;


        }
        }


        val classesWithPeopleAssigned = evaluator.transformGenotype(bestS)
        val (_, violations) = evaluator.evaluateWithViolations(
            classesWithPeopleAssigned,
            true
        )
        return SolverResult(
            classesWithPeopleAssigned,
            Statistics(
                time.inWholeMilliseconds / 1000.0,
                mapOf(
                    "Happiness" to calculateHappiness(classesWithPeopleAssigned).toString(),
                    "Violations" to violations.toString(),
                    "Iterations" to iterations.toString(),
                    "Fitness" to bestC.toString(),
                )
            )
        ).right()
    }

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()


    }

    private val K = 5
    private val n = 100

    private fun calculateInitialTemperature(
        starter: Genotype<BitGene>,
        evaluationFunction: (g: Genotype<BitGene>) -> Int
    ): Double {
        val allRandomFitness = (0..<n)
            .map { starter.newInstance() }
            .map { evaluationFunction(it) }

        val mean = allRandomFitness.average()

        val variance = allRandomFitness.sumOf { (it - mean).pow(2) } / n


        return K * variance
    }


    @Suppress("SameParameterValue")
    private fun coolDown(
        iteration: Long,
        initialTemperature: Double,
        logarithmicRate: Double,
        exponentialRate: Double,
        logarithmicProbability: Double
    ): Double {

        return if (true) {
            val exponentialCoolDown = exponentialCoolDown(iteration, initialTemperature, exponentialRate)
//            println("exponentialCoolDown: $exponentialCoolDown")
            exponentialCoolDown
        } else {
            val logarithmicCoolDown = logarithmicCoolDown(iteration, initialTemperature, logarithmicRate)
//            println("logarithmicCoolDown: $logarithmicCoolDown")
            logarithmicCoolDown

        }
    }

    private fun logarithmicCoolDown(iteration: Long, initialTemperature: Double, rate: Double): Double {
        return (rate * initialTemperature) / ln(1.0 + iteration)
    }

    private fun exponentialCoolDown(iteration: Long, initialTemperature: Double, rate: Double): Double {
        return rate.pow(iteration.toInt()) * initialTemperature
    }

    private fun allExistingOverlaps(slots: List<ClassSlot>) =
        overlappingSlotsPairs(slots).groupBy(
            { it.first.id },
            { it.second.id })
            .mapValues { it.value.toSet() }
}