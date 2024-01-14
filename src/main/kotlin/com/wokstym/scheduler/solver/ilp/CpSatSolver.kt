package com.wokstym.scheduler.solver.ilp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.ortools.Loader
import com.google.ortools.sat.*
import com.wokstym.scheduler.domain.*
import com.wokstym.scheduler.solver.Solver
import com.wokstym.scheduler.solver.overlappingSlotsPairs

class CpSatSolver : Solver {

    init {
        Loader.loadNativeLibraries()
    }

    override val algorithm: Solver.Algorithm = Solver.Algorithm.CP_SAT

    override fun calculateSchedule(
        students: List<Person>,
        slots: List<ClassSlot>
    ): Either<Solver.Error, SolverResult> {

        val slotsByName: Map<SlotName, List<ClassSlot>> = slots.groupBy { it.name }
        val model = CpModel()

        val db = createVariablesDb(students, slots, model)

        ensureSlotCapacity(slots, students, db, model)
        ensureNoOverlappingSlots(slots, students, db, model)
        ensureStudentBeAssignedToCorrectAmountOfClasses(students, slotsByName, db, model)

        val happiness = createHappinessExpression(students, slots, db)
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


            val results = transformToClassesWithPeopleAssigned(slots, students, db, solver)
            println("Achieved happiness: ${solver.objectiveValue()}")

            return SolverResult(
                results, Statistics(
                    timeInSeconds = solver.wallTime(),
                    variousStats = mapOf(
                        "conflicts" to solver.numConflicts().toString(),
                        "branches" to solver.numBranches().toString(),
                        "happiness" to solver.objectiveValue().toString()
                    )
                )
            ).right()


        }
        System.out.printf("No optimal solution found !")
        return Solver.Error.NoViableSolution().left()
    }

    private fun transformToClassesWithPeopleAssigned(
        slots: List<ClassSlot>,
        students: List<Person>,
        db: SolverVariablesDb<Literal>,
        solver: CpSolver
    ): ArrayList<ClassWithPeopleAssigned> {
        val results = ArrayList<ClassWithPeopleAssigned>()


        for (slot in slots) {
            val peopleInSlot = ArrayList<Person>()

            for (person in students) {
                val literal = db.get(person.id, slot)
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
        return results
    }

    private fun createHappinessExpression(
        students: List<Person>,
        slots: List<ClassSlot>,
        db: SolverVariablesDb<Literal>
    ): LinearExprBuilder {
        val happiness: LinearExprBuilder = LinearExpr.newBuilder()

        for (person in students) {
            for (slot in slots) {
                val literal = db.get(person.id, slot)
                if (literal != null) {
                    happiness.addTerm(literal, person.prefersSlots.getOrDefault(slot.id, 0).toLong())
                }
            }
        }
        return happiness
    }

    // Each student get at most as many slots as he is assigned to for every subject
    private fun ensureStudentBeAssignedToCorrectAmountOfClasses(
        students: List<Person>,
        slotsByName: Map<SlotName, List<ClassSlot>>,
        db: SolverVariablesDb<Literal>,
        model: CpModel
    ) {
        for (person in students) {

            for ((slotName, slotsWithCurrentName) in slotsByName) {
                val amount = person.slotsToFulfill[slotName] ?: 0

                val numShiftsWorked: LinearExprBuilder = LinearExpr.newBuilder()
                for (slot in slotsWithCurrentName) {
                    val literal = db.get(person.id, slot)
                    if (literal != null) {
                        numShiftsWorked.add(literal)
                    }

                }

                model.addEquality(numShiftsWorked, amount.toLong())

            }
        }
    }

    // Each student has no overlapping slots.
    private fun ensureNoOverlappingSlots(
        slots: List<ClassSlot>,
        students: List<Person>,
        db: SolverVariablesDb<Literal>,
        model: CpModel
    ) {
        val allOverlappingSlots = overlappingSlotsPairs(slots)
        for (person in students) {
            for ((firstOverlappingSlot, secondOverlappingSlot) in allOverlappingSlots) {
                val work: MutableList<Literal?> = ArrayList()

                val literal = db.get(person.id, firstOverlappingSlot)
                if (literal != null) {
                    work.add(literal)
                }
                val secondLiteral = db.get(person.id, secondOverlappingSlot)
                if (secondLiteral != null) {
                    work.add(secondLiteral)
                }

                model.addAtMostOne(work)
            }
        }
    }

    // Each slot is assigned to at most seats available.
    private fun ensureSlotCapacity(
        slots: List<ClassSlot>,
        students: List<Person>,
        db: SolverVariablesDb<Literal>,
        model: CpModel
    ) {
        for (slot in slots) {
            val studentsToOne: MutableList<Literal> = ArrayList()
            for (person in students) {
                val literal = db.get(person.id, slot)
                if (literal != null) {
                    studentsToOne.add(literal)
                }
            }
            model.addLessOrEqual(LinearExpr.sum(studentsToOne.toTypedArray()), slot.seats.toLong())
        }
    }

    private fun createVariablesDb(
        students: List<Person>,
        slots: List<ClassSlot>,
        model: CpModel
    ): SolverVariablesDb<Literal> {
        val db = SolverVariablesDb<Literal>()
        for (person in students) {
            for (slot in slots) {
                // Do not create variables for blocked slots
                if (!person.blockedSlotsId.contains(slot.id)) {
                    db.add(
                        person.id,
                        slot.day,
                        slot.id,
                        model.newBoolVar("slot_student" + person.id + "day" + slot.day.ordinal + "slot" + slot.id)
                    )
                }
            }
        }
        return db
    }


}