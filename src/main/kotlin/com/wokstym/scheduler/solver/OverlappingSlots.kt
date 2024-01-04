package com.wokstym.scheduler.solver

import com.wokstym.scheduler.domain.ClassSlot
import com.wokstym.scheduler.domain.DayName


fun overlappingSlotsPairs(slots: List<ClassSlot>): List<Pair<ClassSlot, ClassSlot>> {
    return slots.groupBy { it.day }
        .mapValues { (_, v) -> v.sortedBy { it.startTime } }
        .values.flatMap { slotsFromDay ->
        slotsFromDay.flatMapIndexed { currentIndex, currentSlot ->
            val overlappingSlots = ArrayList<Pair<ClassSlot, ClassSlot>>()

            for (neighbourIndex in (currentIndex + 1) until slotsFromDay.size) {
                val neighbourSlot = slotsFromDay[neighbourIndex]
                if (neighbourSlot.startTime >= currentSlot.endTime)
                    break

                overlappingSlots.add(currentSlot to neighbourSlot)
            }
            overlappingSlots
        }
    }
}