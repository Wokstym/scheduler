package com.wokstym.scheduler.solver.gene.common

import com.wokstym.scheduler.domain.*
import io.jenetics.BitChromosome
import io.jenetics.BitGene
import io.jenetics.Genotype

class GenotypeCacheEvaluator(
    private val overlaps: Map<SlotId, Set<SlotId>>,
    private val slots: List<ClassSlot>,
    private val students: List<Person>,
    private val violationWeight: Int,
    private val cacheFitness: Boolean = true,
) {

    private val cache = HashMap<Genotype<BitGene>, Int>()

    fun evaluateFromGenotype(
        givenGenotype: Genotype<BitGene>
    ): Int {
        val e = {
            evaluateWithViolations(
                transformGenotype(givenGenotype),
                false
            ).first
        }

        return if (cacheFitness)
            cache.getOrPut(givenGenotype) { e() }
        else
            e()
    }

    fun transformGenotype(
        genotype: Genotype<BitGene>
    ): List<ClassWithPeopleAssigned> = genotype.map { it.`as`(BitChromosome::class.java) }
        .mapIndexed { index, chromosome ->
            ClassWithPeopleAssigned(
                slots[index],
                chromosome.mapIndexedNotNull { studentIndex, gen: BitGene ->
                    if (gen.booleanValue()) students[studentIndex] else null
                }
            )
        }




    fun evaluateWithViolations(
        assignedSlots: List<ClassWithPeopleAssigned>,
        addViolationDetails: Boolean = false
    ): Pair<Int, List<String>> {

        val violations = ArrayList<String>()

        val peopleToSlots: List<Pair<Person, List<ClassSlot>>> = assignedSlots
            .flatMap { assignedSlot -> assignedSlot.people.map { it to assignedSlot.classSlot } }
            .groupBy({ (student, _) -> student }, { it.second })
            .map { it.key to it.value }


        var fitness = 0



        // Each slot is assigned to at most seats available.
        for (assignedSlot in assignedSlots) {
            if (assignedSlot.classSlot.seats < assignedSlot.people.size) {
                fitness -= violationWeight * (assignedSlot.people.size - assignedSlot.classSlot.seats)

                if (addViolationDetails)
                    violations.add("Too many students in class ${assignedSlot.classSlot}")
            }
        }

        // No blocked slots
        for ((students, slots) in peopleToSlots) {
            for (slot in slots) {
                if (students.blockedSlotsId.contains(slot.id)) {
                    fitness -= violationWeight
                    if (addViolationDetails)
                        violations.add("Student $students was assigned to blocked slot $slot")
                }
            }
        }

        // Each student has at most one overlapping slot.
        for ((students, slots) in peopleToSlots) {
            val ids = slots.map { it.id }.toSet()

            val overlappingSlotsForThatStudent = slots
                .filter { overlaps.containsKey(it.id) }
                .map { it.id }
                .flatMap {
                    val o = overlaps[it]!!.filter { ids.contains(it) }
                    if (o.isNotEmpty()) {
                        o + it
                    }
                    o
                }

            fitness -= violationWeight * overlappingSlotsForThatStudent.size
            if (overlappingSlotsForThatStudent.isNotEmpty() && addViolationDetails)
                violations.add("Student $students has overlapping slots")
        }


        // Each student get at most as many slots as he is assigned to for every subject
        for ((students, slots) in peopleToSlots) {
            val groupedByName = slots.groupBy { it.name }
            val assigned: Set<Pair<SlotName, Amount>> = groupedByName
                .mapValues { it.value.size }
                .map { it.key to it.value }
                .toSet()

            fitness -= calculatePenaltyForNotAssigningDesiredSubjectAmount(
                students,
                assigned,
                fitness,
                addViolationDetails,
                violations
            )

//            for ((name, slotsWithSameName) in groupedByName) {
//                val groupedByDay = slotsWithSameName.groupBy { it.day }
//                    .filter { it.value.size > 1 }
//
//                for ((day, incorrectSlots) in groupedByDay) {
//                    fitness -= violationWeight * (incorrectSlots.size - 1)
//                    if (addViolationDetails)
//                        violations.add("Student $students has on $day multiple $name classes")
//                }
//            }

        }

        // Add happiness
        for ((student, slots) in peopleToSlots) {
            for (slot in slots) {
                fitness += student.prefersSlots[slot.id] ?: 0
            }
        }

        return fitness to violations.sorted()

    }

    private fun calculatePenaltyForNotAssigningDesiredSubjectAmount(
        students: Person,
        assigned: Set<Pair<SlotName, Amount>>,
        fitness: Int,
        addViolationDetails: Boolean,
        violations: ArrayList<String>
    ): Int {
        val required: Set<Pair<SlotName, Amount>> = students.slotsToFulfill.entries
            .map { it.key to it.value }
            .toSet()

        val diffs = ((assigned - required) union (required - assigned)).size
        if (diffs > 0 && addViolationDetails)
            violations.add("Student $students has incorrect slots assigned")
        return diffs * violationWeight
    }
}