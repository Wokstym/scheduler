package com.wokstym.scheduler.controller


import com.wokstym.scheduler.solver.gene.annealing.SimulatedAnnealing
import com.wokstym.scheduler.solver.gene.genetic.GeneticSolver
import com.wokstym.scheduler.solver.gene.swarm.SwarmOptimizationSolver
import com.wokstym.scheduler.solver.ilp.CpSatSolver
import com.wokstym.scheduler.solver.ilp.ILPSolver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller {

    // Comment out unwanted solver. Swarm can take ~30 minutes, SA ~50 minutes, Genetic ~3 minuter, CP and ILP ~0.1 seconds.
    val solvers = listOf(
        SwarmOptimizationSolver(),
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
                        }, { solverResult ->
                            SingleResult(
                                algorithm = algorithm,
                                assigned = solverResult.data.map { classWithPeopleAssigned ->
                                    ClassWithStudents(
                                        classId = classWithPeopleAssigned.classSlot.id,
                                        studentsIds = classWithPeopleAssigned.people.map { it.id }
                                    )
                                },
                                stats = solverResult.stats,
                                params = solverResult.params
                            )
                        })
                    })
        )
    }
}