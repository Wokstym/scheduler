package com.wokstym.scheduler.ilp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPObjective
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolver.ResultStatus
import com.google.ortools.linearsolver.MPVariable
import com.google.ortools.sat.*
import com.wokstym.scheduler.*
import com.wokstym.scheduler.solver.Solver


class ILPSolver() : Solver {

    init {
        Loader.loadNativeLibraries()
    }

    override val algorithm: Solver.Algorithm = Solver.Algorithm.ILP

    override fun calculateSchedule(
        students: List<Person>,
        slots: List<ClassSlot>
    ): Either<Solver.Error, SolverResult> {

        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val slotsByName: Map<SlotName, List<ClassSlot>> = slots.groupBy { it.name }

        val shifts = ShiftDb<MPVariable>()

        val model = MPSolver.createSolver("SCIP");

        // Adds literals
        for (person in students) {
            for (slot in slots) {
                if (!person.blockedSlotsId.contains(slot.id)) {
                    shifts.add(
                        person.id,
                        slot.day,
                        slot.id,
                        model.makeBoolVar("slot_student" + person.id + "day" + slot.day.ordinal + "slot" + slot.id)
                    )
                }
            }
        }

        // Each slot is assigned to at most seats available.
        for (slot in slots) {

            val constraint: MPConstraint = model.makeConstraint(0.0, slot.seats.toDouble(), "")
            for (person in students) {
                val literal = shifts.get(person.id, slot)
                if (literal != null) {
                    constraint.setCoefficient(literal, 1.0)
                }
            }
        }


        // Each student has at most one overlapping slot.
        val allOverlappingSlots = overlappingSlots(slotsByDays)
        for (person in students) {
            for ((firstOverlappingSlot, secondOverlappingSlot) in allOverlappingSlots) {

                val constraint: MPConstraint = model.makeConstraint(0.0, 1.0, "")

                val literal = shifts.get(person.id, firstOverlappingSlot)
                if (literal != null) {
                    constraint.setCoefficient(literal, 1.0)
                }
                val secondLiteral = shifts.get(person.id, secondOverlappingSlot)
                if (secondLiteral != null) {
                    constraint.setCoefficient(secondLiteral, 1.0)
                }
            }
        }


        // Each student get at most as many slots as he is assigned to for every subject
        for (person in students) {

            for ((slotName, slotsWithCurrentName) in slotsByName) {
                val amount = person.slotsToFulfill[slotName] ?: 0
                val constraint: MPConstraint = model.makeConstraint(amount.toDouble(), amount.toDouble(), "")

                for (slot in slotsWithCurrentName) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        constraint.setCoefficient(literal, 1.0)
                    }

                }
            }
        }


        val objective: MPObjective = model.objective()

        for (person in students) {
            for (slotsThatDay in slotsByDays.values) {
                for (slot in slotsThatDay) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        objective.setCoefficient(literal, person.prefersSlots.getOrDefault(slot.id, 0).toDouble())
                    }
                }
            }
        }

        objective.setMaximization()

        // Creates a solver and solves the model.
        val status: ResultStatus = model.solve()
//        val status: CpSolverStatus = solver.solve(model)
//        println(resultStatus)
        println(objective.value())



        println(
            """
        |Statistics
        |  branch-and-bound nodes: ${model.nodes()}
        |  iterations: ${model.iterations()}
        |  wall time: ${model.wallTime() / 1000.0} s
    """.trimMargin()
        )


        if (status === ResultStatus.OPTIMAL || status === ResultStatus.FEASIBLE) {
            println("Solved!")


            val results = ArrayList<ClassWithPeopleAssigned>()


            for (slot in slots) {
                val peopleInSlot = ArrayList<Person>()


                for (person in students) {
                    val literal = shifts.get(person.id, slot)
                    if (literal != null) {
                        if (literal.solutionValue() > 0.5) {
                            peopleInSlot.add(person)
                        }
                    }
                }
                results.add(
                    ClassWithPeopleAssigned(
                        slot,
                        peopleInSlot,

                    )
                )
            }


            println("Achieved happiness: ${objective.value()}")

            return SolverResult(results, Statistics(
                timeInSeconds = model.wallTime() / 1000.0, variousStats = mapOf(
                    "branch-and-bound nodes" to model.nodes().toString(),
                    "iterations" to model.iterations().toString()
                )
            )).right()


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