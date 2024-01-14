package com.wokstym.scheduler.solver.gene.swarm

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
import io.jenetics.util.Seq
import kotlin.math.exp
import kotlin.random.Random
import kotlin.time.measureTimedValue


class SwarmOptimizationSolver(
    // Cognitive coeﬀicient
    private val c1: Double = 3.4,
    // Social coeﬀicient
    private val c2: Double = 3.4,
    // Inertia coeﬀicient
    private val w: Double = 1.2,
    // If after moving by velocity should mutate
    private val mutate: Boolean = false,
    private val maxVelocity: Double = 6.0,
    private val population: Int = 30,
    private val iterations: Int = 10000
) : Solver {

    override val algorithm = Solver.Algorithm.SWARM

    private val mutator = CustomSwapMutator<Int>(mutationProbability = 1.0)


    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {
        val (studentsWithoutLectures, slotsWithoutLectures) = filterLectures(students, slots)

        val overlaps = allExistingOverlaps(slotsWithoutLectures)
        val evaluator = GenotypeCacheEvaluator(overlaps, slotsWithoutLectures, studentsWithoutLectures, 80, false)


        val (result, timeTaken) = measureTimedValue {
            var particles = (0..population)
                .map { createRandomStarterGenotype(studentsWithoutLectures, slotsWithoutLectures) }
                .map {
                    val fitness = evaluator.evaluateFromGenotype(it)
                    Particle(
                        value = it,
                        fitness = fitness,
                        velocity = Array(slotsWithoutLectures.size * studentsWithoutLectures.size) { 0.0 },
                        bestPosition = BestParticle(
                            value = it,
                            fitness = fitness
                        )
                    )
                }


            var bestGenotype = particles.maxBy { it.fitness }.toBestParticle()

            for (i in 0..iterations) {

                particles = particles.map { move(it, bestGenotype, evaluator) }
                val potentialBest = particles.maxBy { it.fitness }.toBestParticle()
                if (potentialBest.fitness > bestGenotype.fitness) {
                    bestGenotype = potentialBest
                }
                if (i % 200 == 0) {
                    println(
                        "$i ${bestGenotype.fitness} ${
                            particles.maxBy { it.fitness }.velocity.joinToString(", ") {
                                it.format(
                                    1
                                )
                            }
                        }")
                }
            }

            bestGenotype
        }

        val bestGenotype = result.value
        val classesWithPeopleAssigned = evaluator.transformGenotype(bestGenotype)

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
                    "Fitness" to result.fitness.toString(),
                    "Violations" to violations.toString(),
                )
            ),
            mapOf(
                "Cognitive coefficient (c1)" to c1.toString(),
                "Social coefficient (c2)" to c2.toString(),
                "Inertia coefficient (w)" to w.toString(),
                "Mutate" to mutate.toString(),
                "Max Velocity" to maxVelocity.toString(),
                "Iterations" to iterations.toString(),
                "Population" to population.toString()
            )
        ).right()
    }

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()
    }

    private fun move(current: Particle, bestGlobal: BestParticle, evaluator: GenotypeCacheEvaluator): Particle {
        val newVelocity = calculateVelocity(current, bestGlobal)
        var newGenotype = newVelocity.map {
            BitGene.of(Random.nextDouble() < sigmoidFunction(it))
        }.chunked(current.value.chromosome().length())
            .map { current.value.chromosome().newInstance(ISeq.of(it)) }
            .let { Genotype.of(it) }

        var fitness = evaluator.evaluateFromGenotype(newGenotype)

        if (mutate) {
            val mutatedGenotype = mutator.alter(Seq.of(Phenotype.of(newGenotype, 0L)), 0).population[0].genotype()
            val mutatedFitness = evaluator.evaluateFromGenotype(mutatedGenotype)
            if (mutatedFitness > fitness) {
                fitness = mutatedFitness
                newGenotype = mutatedGenotype
            }
        }

        val newLocalBest = if (fitness > current.bestPosition.fitness)
            BestParticle(newGenotype, fitness)
        else
            current.bestPosition

        return Particle(
            newGenotype,
            fitness,
            newVelocity,
            newLocalBest
        )
    }

    private fun calculateBitVelocity(
        previousVelocity: Double,
        currentBit: BitGene,
        bestLocalBit: BitGene,
        bestGlobalBit: BitGene
    ): Double {
        return (w * previousVelocity +
                c1 * Random.nextDouble() * (bestLocalBit.ordinal - currentBit.ordinal) +
                c2 * Random.nextDouble() * (bestGlobalBit.ordinal - currentBit.ordinal))
            .coerceIn(-maxVelocity, maxVelocity)
    }

    private fun Genotype<BitGene>.getFromGlobalIndex(index: Int): BitGene {
        val chromosomeLength = chromosome().length()

        val chromosomeIndex: Int = index / chromosomeLength
        val bitGeneIndex: Int = index % chromosomeLength

        return this[chromosomeIndex][bitGeneIndex]

    }

    private fun calculateVelocity(current: Particle, bestGlobal: BestParticle): Array<Double> {
        return Array(current.velocity.size) { index ->
            calculateBitVelocity(
                previousVelocity = current.velocity[index],
                currentBit = current.value.getFromGlobalIndex(index),
                bestLocalBit = current.bestPosition.value.getFromGlobalIndex(index),
                bestGlobalBit = bestGlobal.value.getFromGlobalIndex(index),
            )
        }
    }

    private fun sigmoidFunction(velocity: Double): Double {
        return 1.0 / (1.0 + exp(-velocity))
    }
}