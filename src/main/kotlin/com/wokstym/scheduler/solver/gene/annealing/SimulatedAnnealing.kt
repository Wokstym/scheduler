package com.wokstym.scheduler.solver.gene.annealing

import arrow.core.Either
import arrow.core.right
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.allExistingOverlaps
import com.wokstym.scheduler.solver.gene.common.CustomSwapMutator
import com.wokstym.scheduler.solver.gene.common.GenotypeCacheEvaluator
import com.wokstym.scheduler.solver.gene.common.createRandomStarterGenotype
import com.wokstym.scheduler.solver.gene.common.filterLectures
import com.wokstym.scheduler.utils.format
import io.jenetics.BitGene
import io.jenetics.Genotype
import io.jenetics.Phenotype
import io.jenetics.util.ISeq
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.measureTimedValue

class SimulatedAnnealing(

    // indicates how many changes have to happen at given temperature cycle to count the iteration as frozen
    private val freezeMinimalChangesPercentage: Double = 0.1,

    // indicates how many frozen iterations need to happen to stop annealing
    private val freezeLimit: Int = 300,

    // by how much we increase amount of tries at given temperature
    private val trialMultiplier: Int = 10,

    // how many changes percentage need to happen to reduce temperature faster
    private val fastReductionMinimalChangesPercentage: Double = 0.7,

    private val fastCoolingFactor: Double = 0.80,
    private val slowCoolingFactor: Double = 0.99,

    private val bitFlipInsteadOfSwapMutationProbability: Double = 0.5

) : Solver {
    override val algorithm = Solver.Algorithm.SA

    private val mutator = CustomSwapMutator<Int>(bitFlipInsteadOfSwapMutationProbability, mutationProbability = 1.0)

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
        val (studentsWithoutLectures, slotsWithoutLectures) = filterLectures(students, slots)

        val overlaps = allExistingOverlaps(slotsWithoutLectures)
        val evaluator = GenotypeCacheEvaluator(overlaps, slotsWithoutLectures, studentsWithoutLectures, 80, false)

        val starterGenotype = createRandomStarterGenotype(studentsWithoutLectures, slotsWithoutLectures)
        val variablesCount = slotsWithoutLectures.size * studentsWithoutLectures.size


        var iterations = 0L

        println(trialMultiplier * variablesCount)

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

                    if (neighbour.fitness() > current.fitness()) {
                        changes += 1
                        current = neighbour

                    } else if (neighbour.fitness() < current.fitness() || neighbour.genotype() != current.genotype()) {
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
                    slowCoolingFactor
                println("Temperature: ${temperature.format(2)}, best cost: ${best.fitness()}, current cost: ${current.fitness()}, $freezeCount $changesPercentage")

                if (changesPercentage < freezeMinimalChangesPercentage)
                    freezeCount += 1
                else
                    freezeCount = 0

            }
            best
        }

        println(result)

        val classesWithPeopleAssigned = evaluator.transformGenotype(result.genotype())
        val (_, violations) = evaluator.evaluateWithViolations(
            classesWithPeopleAssigned,
            true
        )
        return SolverResult(
            evaluator.addLectures(classesWithPeopleAssigned, slots),
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
                "bitFlipInsteadOfSwapMutationProbability" to bitFlipInsteadOfSwapMutationProbability.toString()
            )
        ).right()
    }

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()


    }

    private val k = 5
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

        val d = k * variance
        println("Initial temperature: $d")
        return d
    }
}