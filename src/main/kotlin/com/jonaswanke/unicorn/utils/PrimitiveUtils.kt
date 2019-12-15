package com.jonaswanke.unicorn.utils

fun <T> Boolean.thenTake(block: () -> T): T? {
    return if (this) block() else null
}
