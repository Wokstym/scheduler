package com.wokstym.scheduler.ilp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.ortools.Loader
import com.google.ortools.sat.*
import com.wokstym.scheduler.*
import com.wokstym.scheduler.solver.Solver

class CpSatSolver() : Solver {

    init {
        Loader.loadNativeLibraries()
    }

    override val algorithm: Solver.Algorithm = Solver.Algorithm.CP_SAT

    override fun calculateSchedule(
        students: List<Person>,
        slots: List<ClassSlot>
    ): Either<Solver.Error, SolverResult> {

        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val slotsByName: Map<SlotName, List<ClassSlot>> = slots.groupBy { it.name }

        val shifts = ShiftDb<Literal>()

        val model = CpModel()

        // Adds literals
        for (person in students) {
            for (slot in slots) {
                if (!person.blockedSlotsId.contains(slot.id)) {
                    shifts.add(
                        person.id,
                        slot.day,
                        slot.id,
                        model.newBoolVar("slot_student" + person.id + "day" + slot.day.ordinal + "slot" + slot.id)
                    )
                }
            }
        }

        // Each slot is assigned to at most seats available.
        for (slot in slots) {
            val studentsToOne: MutableList<Literal> = ArrayList()
            for (person in students) {
                val literal = shifts.get(person.id, slot)
                if (literal != null) {
                    studentsToOne.add(literal)
                }
            }
            model.addLessOrEqual(LinearExpr.sum(studentsToOne.toTypedArray()), slot.seats.toLong())
        }


        // Each student has at most one overlapping slot.
        val allOverlappingSlots = overlappingSlots(slotsByDays)
        for (person in students) {
            for ((firstOverlappingSlot, secondOverlappingSlot) in allOverlappingSlots) {
                val work: MutableList<Literal?> = ArrayList()

                val literal = shifts.get(person.id, firstOverlappingSlot)
                if (literal != null) {
                    work.add(literal)
                }
                val secondLiteral = shifts.get(person.id, secondOverlappingSlot)
                if (secondLiteral != null) {
                    work.add(secondLiteral)
                }

                model.addAtMostOne(work)
            }
        }


        // Each student get at most as many slots as he is assigned to for every subject
        for (person in students) {

            for ((slotName, slotsWithCurrentName) in slotsByName) {
                val amount = person.slotsToFulfill[slotName] ?: 0

                val numShiftsWorked: LinearExprBuilder = LinearExpr.newBuilder()
                for (slot in slotsWithCurrentName) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        numShiftsWorked.add(literal)
                    }

                }

                model.addEquality(numShiftsWorked, amount.toLong())

            }
        }


        val happiness: LinearExprBuilder = LinearExpr.newBuilder()

        for (person in students) {
            for (slotsThatDay in slotsByDays.values) {
                for (slot in slotsThatDay) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        happiness.addTerm(literal, person.prefersSlots.getOrDefault(slot.id, 0).toLong())
                    }
                }
            }
        }

        model.maximize(happiness)

        // Creates a solver and solves the model.
        val solver = CpSolver()
        val status: CpSolverStatus = solver.solve(model)

        println(
            """
        |Statistics
        |  conflicts: ${solver.numConflicts()}
        |  branches : ${solver.numBranches()}
        |  wall time: ${String.format("%.6f", solver.wallTime())} s
    """.trimMargin()
        )


        if (status === CpSolverStatus.OPTIMAL || status === CpSolverStatus.FEASIBLE) {
            println("Solved!")


            val results = ArrayList<ClassWithPeopleAssigned>()


            for (slot in slots) {
                val peopleInSlot = ArrayList<Person>()


                for (person in students) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        if (solver.booleanValue(literal)) {
                            peopleInSlot.add(person)
                        }
                    }
                }
                results.add(
                    ClassWithPeopleAssigned(
                        slot, peopleInSlot
                    )
                )
            }


            val achievedHappiness = results.sumOf { classWithPeople ->
                classWithPeople.people.sumOf {
                    it.prefersSlots.getOrDefault(
                        classWithPeople.classSlot.id,
                        0
                    )
                }
            }
            println("Achieved happiness: $achievedHappiness")

            return SolverResult(
                results, Statistics(
                    timeInSeconds = solver.wallTime(),
                    variousStats = mapOf(
                        "conflicts" to solver.numConflicts().toString(),
                        "branches" to solver.numBranches().toString()
                    )
                )
            ).right()


        }
        System.out.printf("No optimal solution found !")
        return Solver.Error.NoViableSolution().left()
    }


    fun overlappingSlots(slotsByDays: Map<DayName, List<ClassSlot>>): List<Pair<ClassSlot, ClassSlot>> {
        return slotsByDays.values.flatMap { slotsFromDay ->
            slotsFromDay.flatMapIndexed { currentIndex, currentSlot ->
                val overlappingSlots = ArrayList<Pair<ClassSlot, ClassSlot>>()

                for (neighbourIndex in (currentIndex + 1) until slotsFromDay.size) {
                    val neighbourSlot = slotsFromDay[neighbourIndex]
                    if (neighbourSlot.startTime >= currentSlot.endTime)
                        break

                    overlappingSlots.add(currentSlot to neighbourSlot)
                }
                overlappingSlots
            }
        }
    }


}