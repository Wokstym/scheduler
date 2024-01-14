package com.wokstym.scheduler.solver.ilp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPObjective
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolver.ResultStatus
import com.google.ortools.linearsolver.MPVariable
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.overlappingSlotsPairs


class ILPSolver : Solver {

    init {
        Loader.loadNativeLibraries()
    }

    override val algorithm: Solver.Algorithm = Solver.Algorithm.ILP

    override fun calculateSchedule(
        students: List<Person>,
        slots: List<ClassSlot>
    ): Either<Solver.Error, SolverResult> {
        val model = MPSolver.createSolver("SCIP")
        val db = createVariablesDb(students, slots, model)

        val slotsByName: Map<SlotName, List<ClassSlot>> = slots.groupBy { it.name }

        ensureSlotCapacity(slots, model, students, db)
        ensureNoOverlappingSlots(slots, students, model, db)
        ensureStudentBeAssignedToCorrectAmountOfClasses(students, slotsByName, model, db)

        val objective: MPObjective = createHapinessObjective(model, students, slots, db)

        val status = model.solve()



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

            val results = transformToClassesWithPeopleAssigned(slots, students, db)
            println("Achieved happiness: ${objective.value()}")

            return SolverResult(
                results, Statistics(
                    timeInSeconds = model.wallTime() / 1000.0, variousStats = mapOf(
                        "branch-and-bound nodes" to model.nodes().toString(),
                        "iterations" to model.iterations().toString(),
                        "happiness" to objective.value().toString()
                    )
                )
            ).right()


        }
        println("No optimal solution found !")
        return Solver.Error.NoViableSolution().left()
    }

    private fun transformToClassesWithPeopleAssigned(
        slots: List<ClassSlot>,
        students: List<Person>,
        db: SolverVariablesDb<MPVariable>
    ): ArrayList<ClassWithPeopleAssigned> {
        val results = ArrayList<ClassWithPeopleAssigned>()

        for (slot in slots) {
            val peopleInSlot = ArrayList<Person>()
            for (person in students) {
                val literal = db.get(person.id, slot)
                if (literal != null) {
                    if (literal.solutionValue() > 0.5) {
                        peopleInSlot.add(person)
                    }
                }
            }
            results.add(
                ClassWithPeopleAssigned(
                    slot,
                    peopleInSlot
                )
            )
        }
        return results
    }

    private fun createHapinessObjective(
        model: MPSolver,
        students: List<Person>,
        slots: List<ClassSlot>,
        db: SolverVariablesDb<MPVariable>
    ): MPObjective {
        val objective: MPObjective = model.objective()

        for (person in students) {
            for (slot in slots) {
                val literal = db.get(person.id, slot)
                if (literal != null) {
                    objective.setCoefficient(literal, person.prefersSlots.getOrDefault(slot.id, 0).toDouble())
                }
            }
        }

        objective.setMaximization()
        return objective
    }

    // Each student get at most as many slots as he is assigned to for every subject
    private fun ensureStudentBeAssignedToCorrectAmountOfClasses(
        students: List<Person>,
        slotsByName: Map<SlotName, List<ClassSlot>>,
        model: MPSolver,
        db: SolverVariablesDb<MPVariable>
    ) {

        for (person in students) {
            for ((slotName, slotsWithCurrentName) in slotsByName) {
                val amount = person.slotsToFulfill[slotName] ?: 0
                val constraint: MPConstraint = model.makeConstraint(amount.toDouble(), amount.toDouble(), "")

                for (slot in slotsWithCurrentName) {
                    val literal = db.get(person.id, slot)
                    if (literal != null) {
                        constraint.setCoefficient(literal, 1.0)
                    }

                }
            }
        }
    }

    // Each student has no overlapping slots.
    private fun ensureNoOverlappingSlots(
        slots: List<ClassSlot>,
        students: List<Person>,
        model: MPSolver,
        db: SolverVariablesDb<MPVariable>
    ) {
        val allOverlappingSlots = overlappingSlotsPairs(slots)
        for (person in students) {
            for ((firstOverlappingSlot, secondOverlappingSlot) in allOverlappingSlots) {

                val constraint: MPConstraint = model.makeConstraint(0.0, 1.0, "")

                val literal = db.get(person.id, firstOverlappingSlot)
                if (literal != null) {
                    constraint.setCoefficient(literal, 1.0)
                }
                val secondLiteral = db.get(person.id, secondOverlappingSlot)
                if (secondLiteral != null) {
                    constraint.setCoefficient(secondLiteral, 1.0)
                }
            }
        }
    }

    // Each slot is assigned to at most seats available.
    private fun ensureSlotCapacity(
        slots: List<ClassSlot>,
        model: MPSolver,
        students: List<Person>,
        db: SolverVariablesDb<MPVariable>
    ) {
        for (slot in slots) {
            val constraint: MPConstraint = model.makeConstraint(0.0, slot.seats.toDouble(), "")
            for (person in students) {
                val literal = db.get(person.id, slot)
                if (literal != null) {
                    constraint.setCoefficient(literal, 1.0)
                }
            }
        }
    }

    private fun createVariablesDb(
        students: List<Person>,
        slots: List<ClassSlot>,
        model: MPSolver
    ): SolverVariablesDb<MPVariable> {
        val db = SolverVariablesDb<MPVariable>()
        for (person in students) {
            for (slot in slots) {
                // Do not create variables for blocked slots
                if (!person.blockedSlotsId.contains(slot.id)) {
                    db.add(
                        person.id,
                        slot.day,
                        slot.id,
                        model.makeBoolVar("slot_student" + person.id + "day" + slot.day.ordinal + "slot" + slot.id)
                    )
                }
            }
        }
        return db
    }


}