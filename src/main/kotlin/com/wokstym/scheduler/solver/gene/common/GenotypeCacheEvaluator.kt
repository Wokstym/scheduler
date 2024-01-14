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

    fun addLectures(
        assigned: List<ClassWithPeopleAssigned>,
        originalSlots: List<ClassSlot>
    ): List<ClassWithPeopleAssigned> {
        val lectures = originalSlots.filter { !it.name.contains("-") }

        return assigned + lectures.map {
            ClassWithPeopleAssigned(
                classSlot = it,
                people = students
            )
        }

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

        fitness -= ensureSlotCapacity(assignedSlots, addViolationDetails, violations)
        fitness -= ensureNoBlockedSlots(peopleToSlots, addViolationDetails, violations)
        fitness -= ensureNoOverlappingSlots(peopleToSlots, addViolationDetails, violations)
        fitness -= ensureStudentBeAssignedToCorrectAmountOfClasses(peopleToSlots, addViolationDetails, violations)

        fitness += calculateHappiness(peopleToSlots)

        return fitness to violations.sorted()

    }

    // Each student get at most as many slots as he is assigned to for every subject
    private fun ensureStudentBeAssignedToCorrectAmountOfClasses(
        peopleToSlots: List<Pair<Person, List<ClassSlot>>>,
        addViolationDetails: Boolean,
        violations: ArrayList<String>
    ): Int {
        var fitnessPenalty = 0
        for ((students, slots) in peopleToSlots) {
            val groupedByName = slots.groupBy { it.name }
            val assigned: Set<Pair<SlotName, Amount>> = groupedByName
                .mapValues { it.value.size }
                .map { it.key to it.value }
                .toSet()

            fitnessPenalty += calculatePenaltyForNotAssigningDesiredSubjectAmount(
                students,
                assigned,
                addViolationDetails,
                violations
            )
        }
        return fitnessPenalty
    }

    private fun calculateHappiness(
        peopleToSlots: List<Pair<Person, List<ClassSlot>>>
    ): Int {
        var happiness = 0
        for ((student, slots) in peopleToSlots) {
            for (slot in slots) {
                happiness += student.prefersSlots[slot.id] ?: 0
            }
        }
        return happiness
    }

    // Each student has no overlapping slots.
    private fun ensureNoOverlappingSlots(
        peopleToSlots: List<Pair<Person, List<ClassSlot>>>,
        addViolationDetails: Boolean,
        violations: ArrayList<String>
    ): Int {
        var fitnessPenalty = 0
        for ((students, slots) in peopleToSlots) {
            val ids = slots.map { it.id }.toSet()

            val overlappingSlotsForThatStudent = slots
                .filter { overlaps.containsKey(it.id) }
                .map { it.id }
                .flatMap { slotId ->
                    val o = overlaps[slotId]!!.filter { ids.contains(it) }
                    if (o.isNotEmpty()) {
                        o + slotId
                    }
                    o
                }

            fitnessPenalty += violationWeight * overlappingSlotsForThatStudent.size
            if (overlappingSlotsForThatStudent.isNotEmpty() && addViolationDetails)
                violations.add("Student $students has overlapping slots")
        }
        return fitnessPenalty
    }

    private fun ensureNoBlockedSlots(
        peopleToSlots: List<Pair<Person, List<ClassSlot>>>,
        addViolationDetails: Boolean,
        violations: ArrayList<String>
    ): Int {
        var fitnessPenalty = 0
        for ((students, slots) in peopleToSlots) {
            for (slot in slots) {
                if (students.blockedSlotsId.contains(slot.id)) {
                    fitnessPenalty += violationWeight
                    if (addViolationDetails)
                        violations.add("Student $students was assigned to blocked slot $slot")
                }
            }
        }
        return fitnessPenalty
    }

    // Each slot is assigned to at most seats available.
    private fun ensureSlotCapacity(
        assignedSlots: List<ClassWithPeopleAssigned>,
        addViolationDetails: Boolean,
        violations: ArrayList<String>
    ): Int {
        var fitnessPenalty = 0
        for (assignedSlot in assignedSlots) {
            if (assignedSlot.classSlot.seats < assignedSlot.people.size) {
                fitnessPenalty += ((violationWeight * 0.8) * (assignedSlot.people.size - assignedSlot.classSlot.seats)).toInt()

                if (addViolationDetails)
                    violations.add("Too many students in class ${assignedSlot.classSlot}")
            }
        }
        return fitnessPenalty
    }

    private fun calculatePenaltyForNotAssigningDesiredSubjectAmount(
        students: Person,
        assigned: Set<Pair<SlotName, Amount>>,
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