package com.wokstym.scheduler

import java.time.LocalDateTime
import java.time.LocalTime


typealias SlotId = Int
typealias StudentId = Int
typealias Points = Int
typealias Amount = Int
typealias SlotName = String
typealias PersonName = String


data class ClassSlot(
    val id: SlotId,
    val name: String,
    val day: DayName,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val seats: Int = 1
) {
    override fun toString(): String {
        return "ClassSlot(id=$id)"
    }
}

data class Person(
    val id: StudentId,
    val name: String,
    val slotsToFulfill: Map<SlotName, Amount>,
    val prefersSlots: Map<SlotId, Points>,
    val blockedSlotsId: Set<Int>
)

data class Event(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime
)

data class Statistics(
    val timeInSeconds: Double,
    val variousStats: Map<String, String>
)

data class ClassWithPeopleAssigned(
    val classSlot: ClassSlot,
    val people: List<Person>,
)

data class SolverResult(
    val data: List<ClassWithPeopleAssigned>,
    val stats: Statistics

)

fun <T> HashMap<StudentId, HashMap<DayName, HashMap<SlotId, T>>>.add(
    studentId: StudentId,
    day: DayName,
    slotId: SlotId,
    literal: T
) {
    this.getOrPut(studentId) { HashMap() }.getOrPut(day) { HashMap() }[slotId] = literal
}

fun <T> HashMap<StudentId, HashMap<DayName, HashMap<SlotId, T>>>.get(
    studentId: StudentId,
    day: DayName,
    slotId: SlotId
): T? {
    return this.getOrPut(studentId) { HashMap() }.getOrPut(day) { HashMap() }[slotId]
}

