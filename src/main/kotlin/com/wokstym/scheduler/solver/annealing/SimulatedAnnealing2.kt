package com.wokstym.scheduler.solver.annealing

import arrow.core.Either
import arrow.core.right
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.genetic.GenotypeCacheEvaluator
import com.wokstym.scheduler.solver.overlappingSlotsPairs
import com.wokstym.scheduler.utils.format
import io.jenetics.*
import io.jenetics.util.ISeq
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class SimulatedAnnealing2(

    // indicates how many changes have to happen at given temperature cycle to count the iteration as frozen
    private val freezeMinimalChangesPercentage: Double = 0.2,

    // indicates how many frozen iterations need to happen to stop annealing
    private val freezeLimit: Int = 10,

    // by how much we increase amount of tries at given temperature
    private val trialMultiplier: Int = 10,

    // how many changes percentage need to happen to reduce temperature faster
    private val fastReductionMinimalChangesPercentage: Double = 0.7,

    private val fastCoolingFactor: Double = 0.5,
    private val slowCoolingFactor: Double = 0.98

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

        val starterGenotype = createStarterGenotype(students, slots)
        val variablesCount = slots.size * students.size


        var iterations = 0L

        val (result, timeTaken) = measureTimedValue {
            var freezeCount = 0

            var current = Phenotype.of(starterGenotype, 0L, evaluator.evaluateFromGenotype(starterGenotype))
            var best = current
            var temperature = calculateInitialTemperature(starterGenotype) {
                evaluator.evaluateFromGenotype(it)
            }

            while (freezeCount < freezeLimit) {
                var changes = 0
                var trials = 0

                while (trials < trialMultiplier * variablesCount) {
                    iterations += 1
                    trials += 1

                    val neighbour = mutate(evaluator, current, iterations)
                    if (neighbour.fitness() > best.fitness()) {
                        best = neighbour
                    }

                    if (neighbour.fitness() >= current.fitness()) {
                        changes += 1
                        current = neighbour

                    } else {
                        val acceptanceProbability = exp(-(current.fitness() - neighbour.fitness()) / temperature)

                        if (acceptanceProbability > Random.nextDouble()) {
                            changes += 1
                            current = neighbour
                        }
                    }

                }

                val changesPercentage = changes.toDouble() / trials
                temperature *= if (changesPercentage >= fastReductionMinimalChangesPercentage)
                    fastCoolingFactor
                else
                    slowCoolingFactor;


                println("Temperature: ${temperature.format(2)}, best cost: ${best.fitness()}, current cost: ${current.fitness()}, $freezeCount $changesPercentage")

                if (changesPercentage < freezeMinimalChangesPercentage)
                    freezeCount += 1;
                else
                    freezeCount = 0;

            }
            best
        }


        val classesWithPeopleAssigned = evaluator.transformGenotype(result.genotype())
        val (_, violations) = evaluator.evaluateWithViolations(
            classesWithPeopleAssigned,
            true
        )
        return SolverResult(
            classesWithPeopleAssigned,
            Statistics(
                timeTaken.inWholeMilliseconds / 1000.0,
                mapOf(
                    "Happiness" to calculateHappiness(classesWithPeopleAssigned).toString(),
                    "Violations" to violations.toString(),
                    "Iterations" to iterations.toString(),
                    "Fitness" to result.fitness().toString(),
                )
            ),
            mapOf(
                "freezeMinimalChangesPercentage" to freezeMinimalChangesPercentage.toString(),
                "freezeLimit" to freezeLimit.toString(),
                "trialMultiplier" to trialMultiplier.toString(),
                "fastReductionMinimalChangesPercentage" to fastReductionMinimalChangesPercentage.toString(),
                "fastCoolingFactor" to fastCoolingFactor.toString(),
                "slowCoolingFactor" to slowCoolingFactor.toString(),
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


        val d = K * variance
        println("Initial temperature: $d")
        return d
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