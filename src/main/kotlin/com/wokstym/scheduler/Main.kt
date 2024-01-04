package com.wokstym.scheduler

import arrow.core.Either
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.wokstym.scheduler.ilp.CpSatSolver
import com.wokstym.scheduler.solver.Solver
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime


fun main() {
    val slots = listOf(
        ClassSlot(1, "Wprowadzenie do informatyki", DayName.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
        ClassSlot(2, "Analiza matematyczna", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0)),
        ClassSlot(3, "Algebra", DayName.MONDAY, LocalTime.of(12, 0), LocalTime.of(13, 0)),
        ClassSlot(4, "Algebra", DayName.THURSDAY, LocalTime.of(13, 0), LocalTime.of(14, 0)),
        ClassSlot(5, "Applied Mathematics Cw", DayName.THURSDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)),
        ClassSlot(6, "Computer Science", DayName.SATURDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
        ClassSlot(7, "Algebra", DayName.SATURDAY, LocalTime.of(11, 30), LocalTime.of(12, 0), 10),
        ClassSlot(8, "C++", DayName.SATURDAY, LocalTime.of(10, 0), LocalTime.of(12, 0), 10)
    )

    val students = listOf(
        Person(
            1,
            "grzesiek",
            slotsToFulfill = mapOf(),
            prefersSlots = mapOf(1 to 8, 4 to 6),
            blockedSlotsId = setOf(7)
        ),
        Person(
            2,
            "karolina",
            slotsToFulfill = mapOf(),
            prefersSlots = mapOf(1 to 8, 2 to 5),
            blockedSlotsId = setOf(7)
        ),
        Person(
            3,
            "mateusz",
            slotsToFulfill = mapOf(),
            prefersSlots = mapOf(3 to 8, 4 to 5),
            blockedSlotsId = setOf(5, 6, 7)
        ),
        Person(4, "tomek", slotsToFulfill = mapOf(), prefersSlots = mapOf(7 to 7, 8 to 7), blockedSlotsId = setOf()),
        Person(5, "romek", slotsToFulfill = mapOf(), prefersSlots = mapOf(7 to 70, 8 to 70), blockedSlotsId = setOf())
    )



    slots.printEventSlots(fileName = "classes") { slot ->
        val peopleInSlot = ArrayList<Person>()

        for (person in students) {
            if (person.prefersSlots.getOrDefault(slot.id, 0) != 0) {
                peopleInSlot.add(person)
            }
        }
        """
                |${slot.name} (${slot.seats})
                |Choices (${peopleInSlot.size}):
                |${
            peopleInSlot.joinToString(separator = "\n") { person ->
                val weight = person.prefersSlots.getOrDefault(slot.id, 0)
                "\\t${person.name} (w=$weight)"
            }
        }
            """.trimMargin().replace("\n", "\\n")
    }


    println("Happiness pool: ${students.flatMap { it.prefersSlots.values }.sum()}")


    val result: Either<Solver.Error, SolverResult> = CpSatSolver().calculateSchedule(students, slots)

    result.fold({
        println(it.message)
    }, { assigned ->

        val groupedById: Map<SlotId, ClassWithPeopleAssigned> =
            assigned.data.groupBy { it.classSlot.id }
                .mapValues { it.value[0] } // we know ids are unique

        slots.printEventSlots(fileName = "results") { slot ->
            val data = groupedById[slot.id]!!

            """
                |${slot.name} (${slot.seats})
                |Participants (${data.people.size}):
                |${
                data.people.joinToString(separator = "\n") { person ->
                    val weight = person.prefersSlots.getOrDefault(slot.id, 0)
                    "\\t${person.name} (w=$weight)"
                }
            }
            """.trimMargin().replace("\n", "\\n")
        }

    }

    )

}

fun List<ClassSlot>.printEventSlots(fileName: String, populate: (slot: ClassSlot) -> String?) {

    val startDate = LocalDate.of(2023, 10, 8)

    val events = ArrayList<Event>()

    for (slot in this) {
        val res = populate(slot)
        if (res != null) {
            events.add(
                Event(
                    res,
                    startDate.plusDays(slot.day.ordinal.toLong()).atTime(slot.startTime),
                    startDate.plusDays(slot.day.ordinal.toLong()).atTime(slot.endTime)
                )
            )
        }
    }


    File("$fileName.js").printWriter().use { out ->
        out.println("const $fileName = [ \n" + events.joinToString {
            """
            {
                title: "${it.title}",
                start: new Date("${it.start}"),
                end: new Date("${it.end}")
            }
        """.trimIndent()
        } + " \n]")
    }
}
