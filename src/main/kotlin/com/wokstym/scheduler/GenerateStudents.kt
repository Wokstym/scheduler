package com.wokstym.scheduler

import com.google.gson.Gson
import java.io.File
import kotlin.math.abs
import kotlin.random.Random


val names = listOf(
    "Alojza",
    "Leokadiusz",
    "Nataniel",
    "Zygfryda",
    "Maurycjusz",
    "Gniewomira",
    "Leoncja",
    "Symplicjusz",
    "Bernadeta",
    "Maksymilian",
    "Norman",
    "Cezaryna",
    "Dżamil",
    "Marin",
    "Myślimir",
    "Samir",
    "Adriana",
    "Dajmira",
    "Edwin",
    "Godfryd",
    "Krzesisław",
    "Juri",
    "Sława",
    "Pafnucy",
    "Ernestyna",
    "Lilianna",
    "Konrad",
    "Halina",
    "Dzwonimierz",
    "Tybilla",
    "Marlena",
    "Zuzanna",
    "Wiesława",
    "Emil",
    "Nitara",
    "Gracjan",
    "Rachela",
    "Zbigniew",
    "Sulisława",
    "Norma",
    "Leonora",
    "Serwacy",
    "Adelajda",
    "Herman",
    "Ambroży",
    "Przemysł",
    "Chwalimir",
    "Nikodem",
    "Świetlana",
    "Arabella"
)

data class RootGenerate(
    val slots: List<SlotGenerate>,
    val students: List<Student>
)

data class SlotGenerate(
    val id: Long,
    val name: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val seats: Long,
    val groups: Long,
)

data class Student(
    val id: Int,
    val name: String,
    val blockedSlotsId: List<Int>,
    val prefersSlots: Map<String, Int>,
    val slotsToFulfill: Map<String, Int>,
)

fun <K, V> Map<K, V>.prettyPrint() {
    println(this.entries.joinToString("\n") { "${it.key}=${it.value}" })
}


fun <K, V> List<Pair<K, V>>.prettyPrint() {
    println(this.joinToString("\n") { "\"${it.first}\" to  ${it.second}," })
}


fun <T> weightedRandom(list: List<Pair<T, Int>>): Pair<T, Int> {
    val dcd = list.sumOf { it.second }
    val cumulativeSum = ArrayList<Int>()

    var index = 0
    for ((_, weight) in list) {

        cumulativeSum.add(
            if (index == 0)
                weight
            else
                weight + cumulativeSum[index - 1]
        )

        index++
    }


    val rand = Random.nextInt(dcd)

    cumulativeSum.forEachIndexed { index, sum ->
        if (sum >= rand) {
            return list[index]
        }
    }

    return list.last()
}


fun main2() {
    val content = File("input.json").readText(Charsets.UTF_8)

    val gson = Gson()
    val root = gson.fromJson(content, RootGenerate::class.java)

    val byNameLabs = root.slots
        .filter { it.name.contains("-") }
        .groupBy { it.name }
        .entries
        .map { it.value }

    val x = byNameLabs.map { it.map { it to (6 - abs(10 - it.startTime.substring(0, 2).toInt())) } }
    println(x)


//
//   printWanted(root)

    val wantedMap = mapOf(
        "Podstawy Mechaniki" to 1,
        "Podstawy programowania" to 1,
        "Algebra liniowa z geometrią" to 1,
        "Podstawy teorii mnogości i matematyki dyskretnej" to 1,
        "Analiza matematyczna" to 2,
        "Fizyka" to 1,
        "Systemy operacyjne i sieci komputerowe" to 1,
        "Algebra liniowa z geometrią - Ćw" to 1,
        "Analiza matematyczna - Ćw" to 1,
        "Fizyka - Lab" to 1,
        "Podstawy Mechaniki - Ćw" to 1,
        "Podstawy programowania - Lab" to 1,
        "Podstawy programowania - Proj" to 2,
        "Systemy operacyjne i sieci komputerowe - Lab" to 2,
    )

    val niewyklady = wantedMap.entries.filter { it.key.contains("-") }.map { it.key to it.value }
//    nieWyklady.prettyPrint()
//    println(byNameLabs.entries.filter { it.key.contains("-") }.map { it.value.map { it.seats } })


    val students = (1..50).map { id ->
        val prefersSlots = x.map { weightedRandom(it).first.id.toString() to (5..8).random() }.toMap()
        Student(
            id,
            names[id - 1],
            if (Random.nextDouble() <0.0) listOf(root.slots.filter { it.name.contains("-") }
                .random().id.toInt()) else listOf(),
            prefersSlots,
            wantedMap
        )
    }

    val newly = root.copy(students = students)

    File("result.json").writeText(gson.toJson(newly))


//    val firstGroup = pozostałe.map { it.first to it.second.filter { it.groups == 1L }.map { it.seats }.sum() }
//    val secondGroup = pozostałe.map { it.first to it.second.filter { it.groups == 1L }.map { it.seats }.sum() }
//    firstGroup.prettyPrint()
//    println("-----")
//    secondGroup.prettyPrint()


//    println(root.slots.filter { it.groups == 1L }.groupBy { it.name }.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.sumOf { it.seats }}" })
//    println("-----")
//    println(root.slots.filter { it.groups == 2L }.groupBy { it.name }.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.sumOf { it.seats }}" })


}

//fun main() {
//    repeat(10) {
//        println(
//            weightedRandom(
//                listOf(
//                    "y" to 1,
//                    "x" to 2,
//                    "z" to 3,
//                    "as" to 8,
//                )
//            )
//        )
//    }
//}

private fun printWanted(root: RootGenerate) {
    val grouped = root.slots.groupBy { it.name }
//    println(grouped.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.sumOf { it.seats }}" })

    val wyklady = grouped.entries.filter { !it.key.contains("-") }.map { it.key to it.value.size }

    wyklady.prettyPrint()

    val pozostałe = grouped.entries.filter { it.key.contains("-") }.sortedBy { it.key }
        .map { it.key to (it.value.sumOf { it.seats } / 50) }
    pozostałe.prettyPrint()
}

