package com.wokstym.scheduler.domain

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.wokstym.scheduler.utils.format
import java.io.IOException
import java.time.LocalTime


typealias SlotId = Int
typealias StudentId = Int
typealias Points = Int
typealias Amount = Int
typealias SlotName = String


data class ClassSlot(
    val id: SlotId,
    val name: String,
    val day: DayName,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val seats: Int = 1
) {
    override fun toString(): String {
        return "(id=$id \"$name\" $day $startTime-$endTime)"
    }
}

data class Person(
    val id: StudentId,
    val name: String,
    val slotsToFulfill: Map<SlotName, Amount>,
    val prefersSlots: Map<SlotId, Points>,
    val blockedSlotsId: Set<Int>
) {
    override fun toString(): String {
        return "(id=$id \"$name\")"

    }
}

internal class DecimalJsonSerializer : JsonSerializer<Double?>() {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(value: Double?, jgen: JsonGenerator, provider: SerializerProvider?) {
        jgen.writeNumber(value?.format(3))
    }
}

data class Statistics(
    @field:JsonSerialize(using = DecimalJsonSerializer::class)
    val timeInSeconds: Double,
    val variousStats: Map<String, String>
)

data class ClassWithPeopleAssigned(
    val classSlot: ClassSlot,
    val people: List<Person>,
)

data class SolverResult(
    val data: List<ClassWithPeopleAssigned>,
    val stats: Statistics,
    val params: Map<String, String>? = null
)
