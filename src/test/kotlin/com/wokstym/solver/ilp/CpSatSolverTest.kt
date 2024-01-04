package com.wokstym.solver.ilp


import com.wokstym.scheduler.ClassSlot
import com.wokstym.scheduler.DayName
import com.wokstym.scheduler.ilp.CpSatSolver
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.*
import io.kotest.matchers.should
import org.junit.jupiter.api.Test

import java.time.LocalTime

class CpSatSolverTest {

    private fun <A, B, C, D> beEqualInAnyOrder(other: Pair<C, D>) = Matcher<Pair<A, B>> { value ->
        MatcherResult(
            (value.first == other.first && value.second == other.second) || (value.first == other.second && value.second == other.first),
            { "pair was (${value.first}, ${value.second}), but we expected it to be (${other.first}, ${other.second}) or (${other.second}, ${other.first})" },
            { "string should not be (${other.first}, ${other.second}) or (${other.second}, ${other.first})" },
        )
    }

    @Test
    fun overlappingSlots_base() {
        val cpSatSolver = CpSatSolver()

        val firstOverlap = ClassSlot(2, "Applied Mathematics", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))
        val secondOverlap = ClassSlot(3, "Calculus", DayName.MONDAY, LocalTime.of(11, 30), LocalTime.of(12, 30))

        val slots = listOf(
            ClassSlot(1, "Computer Science", DayName.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
            firstOverlap, secondOverlap,
            ClassSlot(4, "Calculus Cw", DayName.THURSDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)),
            ClassSlot(5, "Applied Mathematics Cw", DayName.THURSDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)),
        )
        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val overlapping = cpSatSolver.overlappingSlots(slotsByDays)

        overlapping shouldMatchEach listOf {
            it should beEqualInAnyOrder(firstOverlap to secondOverlap)
        }
    }

    @Test
    fun overlappingSlots_twoNot() {
        val cpSatSolver = CpSatSolver()

        val firstOverlap = ClassSlot(2, "Applied Mathematics", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))
        val secondOverlap = ClassSlot(3, "Calculus", DayName.MONDAY, LocalTime.of(11, 30), LocalTime.of(12, 30))
        val thirdOverlapOnlyWithSecond =
            ClassSlot(4, "Calculus", DayName.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0))

        val slots = listOf(
            firstOverlap, secondOverlap, thirdOverlapOnlyWithSecond
        )
        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val overlapping = cpSatSolver.overlappingSlots(slotsByDays)

        overlapping shouldMatchEach listOf({
            it should beEqualInAnyOrder(firstOverlap to secondOverlap)
        }, {
            it should beEqualInAnyOrder(secondOverlap to thirdOverlapOnlyWithSecond)
        })
    }

    @Test
    fun overlappingSlots_third() {
        val cpSatSolver = CpSatSolver()

        val firstOverlap = ClassSlot(2, "Applied Mathematics", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))
        val forthOverlapWithFirst = ClassSlot(5, "Calculus", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))
        val secondOverlap = ClassSlot(3, "Calculus", DayName.MONDAY, LocalTime.of(11, 30), LocalTime.of(12, 30))
        val thirdOverlapOnlyWithSecond =
            ClassSlot(4, "Calculus", DayName.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0))

        val slots = listOf(
            firstOverlap, secondOverlap, thirdOverlapOnlyWithSecond, forthOverlapWithFirst
        )
        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val overlapping = cpSatSolver.overlappingSlots(slotsByDays)

        overlapping shouldMatchEach listOf({
            it should beEqualInAnyOrder(firstOverlap to forthOverlapWithFirst)
        }, {
            it should beEqualInAnyOrder(firstOverlap to secondOverlap)
        }, {
            it should beEqualInAnyOrder(secondOverlap to forthOverlapWithFirst)
        }, {
            it should beEqualInAnyOrder(secondOverlap to thirdOverlapOnlyWithSecond)
        })
    }

    @Test
    fun overlappingSlots_forth() {
        val cpSatSolver = CpSatSolver()

        val firstOverlap = ClassSlot(2, "Applied Mathematics", DayName.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0))
        val secondOverlap = ClassSlot(3, "Calculus", DayName.MONDAY, LocalTime.of(11, 30), LocalTime.of(12, 0))
        val third = ClassSlot(4, "Calculus", DayName.MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 0))

        val slots = listOf(
            firstOverlap, secondOverlap, third
        )
        val slotsByDays: Map<DayName, List<ClassSlot>> = slots.groupBy { it.day }
            .mapValues { (_, v) -> v.sortedBy { it.startTime } }

        val overlapping = cpSatSolver.overlappingSlots(slotsByDays)

        overlapping shouldMatchEach listOf({
            it should beEqualInAnyOrder(firstOverlap to secondOverlap)
        }, {
            it should beEqualInAnyOrder(firstOverlap to third)
        })
    }
}