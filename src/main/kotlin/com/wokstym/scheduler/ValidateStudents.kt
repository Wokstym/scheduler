package com.wokstym.scheduler

import com.google.gson.Gson
import java.io.File
import kotlin.math.abs
import kotlin.random.Random



fun main() {
    val content = File("result.json").readText(Charsets.UTF_8)

    val gson = Gson()
    val root = gson.fromJson(content, RootGenerate::class.java)


    println(root.students.flatMap { it.slotsToFulfill.entries }.groupBy { it.key }.mapValues { it.value.size }.entries.map { it.value })

//   val x =  root.students.flatMap { it.slotsToFulfill.entries  }.groupBy { it.key }.mapValues { it.value.sumOf { it.value } }
//    println(x)

}


//    val firstGroup = pozostałe.map { it.first to it.second.filter { it.groups == 1L }.map { it.seats }.sum() }
//    val secondGroup = pozostałe.map { it.first to it.second.filter { it.groups == 1L }.map { it.seats }.sum() }
//    firstGroup.prettyPrint()
//    println("-----")
//    secondGroup.prettyPrint()


//    println(root.slots.filter { it.groups == 1L }.groupBy { it.name }.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.sumOf { it.seats }}" })
//    println("-----")
//    println(root.slots.filter { it.groups == 2L }.groupBy { it.name }.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.sumOf { it.seats }}" })




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


