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
import kotlin.time.measureTimedValue

class SimulatedAnnealing(

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

        val starter = createStarterGenotype(students, slots)
        var best = Phenotype.of(starter, 0L, evaluator.evaluateFromGenotype(starter))
        var current = best

        val initialTemperature = calculateInitialTemperature(starter) {
            evaluator.evaluateFromGenotype(it)
        }
        var temperature = initialTemperature

        val temperatureHistory = arrayListOf(0 to temperature)
        val propabilityHistory = arrayListOf(0 to exp(-(current.fitness() - best.fitness()) / temperature))

        val counter = HashMap<Boolean, Int>()

        val (x, time) = measureTimedValue {
            for (i in 0L..iterations) {
                val neighbour = mutate(evaluator, current, i)
                counter.put(neighbour.genotype() != current.genotype(), counter.getOrDefault(neighbour.genotype() != current.genotype(), 0) + 1)
//                println(neighbour.genotype() != current.genotype())
                if (neighbour.fitness() > best.fitness()) {
                    println("best: ${best.fitness()}")
                    best = neighbour
                }


                if (neighbour.fitness() > current.fitness()) {
                    current = neighbour
                } else {
                    val acceptanceProbability = exp(-(current.fitness() - neighbour.fitness()) / temperature)
                    propabilityHistory.add(i.toInt() to acceptanceProbability)

                    if (acceptanceProbability > Random.nextDouble()) {
                        current = neighbour
                    }
                }

                temperature = coolDown(i, initialTemperature, logarithmicRate, exponentialRate, logarithmicProbability)
                temperatureHistory.add(i.toInt() to temperature)


            }
        }

        println(counter)

        println(temperatureHistory.first().second)
        println(propabilityHistory.first().second)

        File("temperatures.txt").printWriter().use { out ->
            out.print(temperatureHistory.joinToString("t "))
        }


        File("propabilities.txt").printWriter().use { out ->
            out.print(propabilityHistory.joinToString("t "))
        }

        val classesWithPeopleAssigned = evaluator.transformGenotype(best.genotype())
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