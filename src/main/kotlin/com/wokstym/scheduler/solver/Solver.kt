package com.wokstym.scheduler.solver

import arrow.core.Either
import com.wokstym.scheduler.ClassSlot
import com.wokstym.scheduler.ClassWithPeopleAssigned
import com.wokstym.scheduler.Person
import com.wokstym.scheduler.SolverResult

interface Solver {
    enum class Algorithm {
        CP_SAT,
        ILP,
        GENETIC
    }

    sealed class Error(val message: String) {
        class NoViableSolution(): Error("Provided data did not allow to create a solution that does not brake any hard constraints")
    }

    val algorithm: Algorithm
    fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Error, SolverResult>

}