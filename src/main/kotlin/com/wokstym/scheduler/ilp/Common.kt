package com.wokstym.scheduler.ilp

import com.wokstym.scheduler.ClassSlot
import com.wokstym.scheduler.DayName
import com.wokstym.scheduler.SlotId
import com.wokstym.scheduler.StudentId

class ShiftDb<T> {
    private val shifts = HashMap<StudentId, HashMap<DayName, HashMap<SlotId, T>>>()


    fun add(studentId: StudentId, day: DayName, slotId: SlotId, T: T) {
        shifts.getOrPut(studentId) { HashMap() }.getOrPut(day) { HashMap() }[slotId] = T
    }

    fun get(studentId: StudentId, slot: ClassSlot): T? {
        return shifts.getOrPut(studentId) { HashMap() }.getOrPut(slot.day) { HashMap() }[slot.id]
    }
}