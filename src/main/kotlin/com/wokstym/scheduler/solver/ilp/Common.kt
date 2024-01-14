package com.wokstym.scheduler.solver.ilp

import com.wokstym.scheduler.domain.ClassSlot
import com.wokstym.scheduler.domain.DayName
import com.wokstym.scheduler.domain.SlotId
import com.wokstym.scheduler.domain.StudentId
import java.util.*

class SolverVariablesDb<T> {
    private val shifts = HashMap<StudentId, EnumMap<DayName, HashMap<SlotId, T>>>()


    fun add(studentId: StudentId, day: DayName, slotId: SlotId, value: T) {
        shifts.getOrPut(studentId) { EnumMap(DayName::class.java) }.getOrPut(day) { HashMap() }[slotId] = value
    }

    fun get(studentId: StudentId, slot: ClassSlot): T? {
        return shifts.getOrPut(studentId) { EnumMap(DayName::class.java) }.getOrPut(slot.day) { HashMap() }[slot.id]
    }
}