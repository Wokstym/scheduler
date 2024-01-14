package com.wokstym.scheduler.solver.gene.swarm

import io.jenetics.BitGene
import io.jenetics.Genotype

data class BestParticle(
    val value: Genotype<BitGene>,
    val fitness: Int,
)

data class Particle(
    val value: Genotype<BitGene>,
    val fitness: Int,
    val velocity: Array<Double>,
    val bestPosition: BestParticle,
) {
    fun toBestParticle(): BestParticle {
        return BestParticle(value, fitness)
    }
}