package com.wokstym.scheduler

import arrow.core.Either
import com.wokstym.scheduler.solver.Solver

class GeneticSolver: Solver {

    override val algorithm: Solver.Algorithm = Solver.Algorithm.GENETIC

    override fun calculateSchedule(students: List<Person>, slots: List<ClassSlot>): Either<Solver.Error, SolverResult> {
        TODO("Not yet implemented")
    }
}