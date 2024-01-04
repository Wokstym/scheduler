package com.wokstym.scheduler.solver.genetic

import arrow.core.Either
import arrow.core.right
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.overlappingSlotsPairs
import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import kotlin.time.TimeSource


class GeneticSolver : Solver {

    override val algorithm: Solver.Algorithm = Solver.Algorithm.GENETIC
    private val timeSource = TimeSource.Monotonic

    private fun calculateHappiness(results: List<ClassWithPeopleAssigned>): Int {
        return results.flatMap { (classSlot, people) ->
            people.map { it.prefersSlots[classSlot.id] ?: 0 }
        }.sum()
    }


    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {


        val allOverlappingSlots = overlappingSlotsPairs(slots)
        println(allOverlappingSlots)


        val overlaps = allOverlappingSlots.groupBy(
            { it.first.id },
            { it.second.id })
            .mapValues { it.value.toSet() }


        val gtf: Genotype<BitGene> = createGenotype(students, slots)
        val engine: Engine<BitGene, Int> = getEngine(gtf, students, slots, overlaps)

        val timeBefore = timeSource.markNow()

        val result: EvolutionResult<BitGene, Int> = engine.stream()
//            .limit(bySteadyFitness(55))
            .limit(1000)
            .collect(EvolutionResult.toBestEvolutionResult())

        val timeAfter = timeSource.markNow()

        val bestGenotype = result.bestPhenotype().genotype()
        val classesWithPeopleAssigned = transformGenotype(bestGenotype, slots, students)


        val (fitness, violations) = evaluate(classesWithPeopleAssigned, overlaps, true)

        return SolverResult(
            classesWithPeopleAssigned,
            Statistics(
                (timeAfter - timeBefore).inWholeMilliseconds / 1000.0,
                mapOf(
                    "happiness" to calculateHappiness(classesWithPeopleAssigned).toString(),
                    "generations" to result.generation().toString(),
                    "fitness" to fitness.toString(),
                    "violations" to violations.toString()
                )
            )
        ).right()
    }

    private fun createGenotype(students: List<Person>, slots: List<ClassSlot>): Genotype<BitGene> {
        return Genotype.of(
            slots.map { BitChromosome.of(students.size, 0.5) }
        )
    }


    private fun getEngine(
        genotype: Genotype<BitGene>,
        students: List<Person>,
        slots: List<ClassSlot>,
        overlaps: Map<SlotId, Set<SlotId>>
    ): Engine<BitGene, Int> {

        return Engine
            .builder({ givenGenotype ->
                evaluate(transformGenotype(givenGenotype, slots, students), overlaps).first
            }, genotype)
            .selector(EliteSelector())
            .alterers(Mutator(0.115),  SinglePointCrossover(0.16))
            .build()
    }

    private fun transformGenotype(
        genotype: Genotype<BitGene>,
        slots: List<ClassSlot>,
        students: List<Person>
    ) = genotype.map { it.`as`(BitChromosome::class.java) }
        .mapIndexed { index, chromosome ->
            ClassWithPeopleAssigned(
                slots[index],
                chromosome.mapIndexedNotNull { studentIndex, gen: BitGene ->
                    if (gen.booleanValue()) students[studentIndex] else null
                }
            )
        }

    private fun evaluate(
        assignedSlots: List<ClassWithPeopleAssigned>,
        overlaps: Map<SlotId, Set<SlotId>>,
        addViolationDetails: Boolean = false
    ): Pair<Int, List<String>> {

        val violationWeight = 80
        val violations = ArrayList<String>()

        val peopleToSlots: List<Pair<Person, List<ClassSlot>>> = assignedSlots
            .flatMap { assignedSlot -> assignedSlot.people.map { it to assignedSlot.classSlot } }
            .groupBy({ (student, _) -> student }, { it.second })
            .map { it.key to it.value }


        var fitness = 0

        // Each slot is assigned to at most seats available.
        for (assignedSlot in assignedSlots) {
            if (assignedSlot.classSlot.seats < assignedSlot.people.size) {
                fitness -= violationWeight
                if (addViolationDetails)
                    violations.add("Too many students in class id = ${assignedSlot.classSlot.id}")
            }
        }

        // No blocked slots
        for ((students, slots) in peopleToSlots) {
            for (slot in slots) {
                if (students.blockedSlotsId.contains(slot.id)) {
                    fitness -= violationWeight
                    if (addViolationDetails)
                        violations.add("Student id = ${students.id} has blocked slot id = ${slot.id}, but was assigned to it")
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
                violations.add("Student id = ${students.id} has overlapping slots with ids = ${overlappingSlotsForThatStudent}")
        }


        // Each student get at most as many slots as he is assigned to for every subject
        for ((students, slots) in peopleToSlots) {
            val assigned: Set<Pair<SlotName, Amount>> = slots.groupBy { it.name }
                .mapValues { it.value.size }
                .map { it.key to it.value }
                .toSet()

            val required: Set<Pair<SlotName, Amount>> = students.slotsToFulfill.entries
                .map { it.key to it.value }
                .toSet()

            val diffs = ((assigned - required) union (required - assigned)).size
            fitness -= diffs * violationWeight
            if (diffs > 0 && addViolationDetails)
                violations.add("Student id = ${students.id} has incorrect slots assigned")
        }

        // Add happiness
        for ((student, slots) in peopleToSlots) {
            for (slot in slots) {
                fitness += student.prefersSlots[slot.id] ?: 0
            }
        }

        return fitness to violations

    }
}