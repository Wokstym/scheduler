package com.wokstym.scheduler.solver.ilp

import com.wokstym.scheduler.domain.ClassSlot
import com.wokstym.scheduler.domain.DayName
import com.wokstym.scheduler.domain.SlotId
import com.wokstym.scheduler.domain.StudentId

class SolverVariablesDb<T> {
    private val shifts = HashMap<StudentId, HashMap<DayName, HashMap<SlotId, T>>>()


    fun add(studentId: StudentId, day: DayName, slotId: SlotId, T: T) {
        shifts.getOrPut(studentId) { HashMap() }.getOrPut(day) { HashMap() }[slotId] = T
    }

    fun get(studentId: StudentId, slot: ClassSlot): T? {
        return shifts.getOrPut(studentId) { HashMap() }.getOrPut(slot.day) { HashMap() }[slot.id]
    }
}