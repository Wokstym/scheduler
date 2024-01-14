package com.wokstym.scheduler.solver.gene.common

import com.wokstym.scheduler.domain.ClassSlot
import com.wokstym.scheduler.domain.Person
import io.jenetics.BitChromosome
import io.jenetics.BitGene
import io.jenetics.Genotype

fun createRandomStarterGenotype(students: List<Person>, slots: List<ClassSlot>): Genotype<BitGene> {
    // Random bits
    return Genotype.of(
        slots.map { BitChromosome.of(students.size, 0.5) }
    )
}

fun filterLectures(students: List<Person>, slots: List<ClassSlot>): Pair<List<Person>, List<ClassSlot>> {
    val slotsWithoutLectures = slots.filter { it.name.contains("-") }
    val studentsWithoutLectures =
        students.map { student -> student.copy(slotsToFulfill = student.slotsToFulfill.filterKeys { it.contains("-") }) }

    return studentsWithoutLectures to slotsWithoutLectures
}