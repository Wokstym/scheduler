package com.wokstym.scheduler.utils

fun Double.format(scale: Int) = "%.${scale}f".format(this)