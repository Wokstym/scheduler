package com.wokstym.scheduler.controller


import com.wokstym.scheduler.solver.gene.annealing.SimulatedAnnealing
import com.wokstym.scheduler.solver.gene.genetic.GeneticSolver
import com.wokstym.scheduler.solver.ilp.CpSatSolver
import com.wokstym.scheduler.solver.ilp.ILPSolver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class Controller {

    val solvers = listOf(
        SimulatedAnnealing(),
        GeneticSolver(),
        CpSatSolver(),
        ILPSolver(),
    )

    @CrossOrigin
    @PostMapping("/generate")
    fun generateShoppingList(@RequestBody request: GenerationRequest): ResponseEntity<GenerationResults> {

        return ResponseEntity.ok(
            GenerationResults(
            solvers.map { it.algorithm to it.calculateSchedule(request.students, request.slots) }
                .map { (algorithm, result) ->
                    result.fold({ error ->
                        SingleResult(algorithm, listOf(), error = ErrorResponse(message = error.message))
                    }, {
                        SingleResult(
                            algorithm = algorithm,
                            assigned = it.data.map { classWithPeopleAssigned ->
                                ClassWithStudents(
                                    classId = classWithPeopleAssigned.classSlot.id,
                                    studentsIds = classWithPeopleAssigned.people.map { it.id }
                                )
                            },
                            stats = it.stats,
                            params = it.params
                        )
                    })
                })
        )
    }

}