package com.wokstym.scheduler


import com.wokstym.scheduler.ilp.CpSatSolver
import com.wokstym.scheduler.ilp.ILPSolver
import com.wokstym.scheduler.solver.Solver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalTime

@RestController
class Controller {

    val solvers = listOf(
        CpSatSolver(),
        ILPSolver()
    )

    @CrossOrigin
    @PostMapping("/generate")
    fun generateShoppingList(@RequestBody request: GenerationRequest): ResponseEntity<GenerationResults> {

        return ResponseEntity.ok(GenerationResults(
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
                            stats = it.stats
                        )
                    })
                })
        )
    }

}