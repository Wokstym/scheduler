package com.wokstym.scheduler.controller

import com.wokstym.scheduler.domain.ClassSlot
import com.wokstym.scheduler.domain.Person
import com.wokstym.scheduler.domain.Statistics
import com.wokstym.scheduler.solver.Solver

data class ClassWithStudents(
    val classId: Int,
    val studentsIds: List<Int>,
)

data class SingleResult(
    val algorithm: Solver.Algorithm,
    val assigned: List<ClassWithStudents>,
    val stats: Statistics? = null,
    val params: Map<String, String>? = null,
    val error: ErrorResponse? = null,
)


data class GenerationRequest(
    val slots: List<ClassSlot>,
    val students: List<Person>
)

data class GenerationResults(
    val results: List<SingleResult>,
)
data class ErrorResponse(
    val message: String,
)
