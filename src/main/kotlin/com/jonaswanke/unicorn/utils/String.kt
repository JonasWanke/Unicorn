package com.jonaswanke.unicorn.utils

fun String.removeSuffix(suffix: CharSequence, ignoreCase: Boolean = false): String {
    if (endsWith(suffix, ignoreCase)) {
        return substring(0, length - suffix.length)
    }
    return this
}
