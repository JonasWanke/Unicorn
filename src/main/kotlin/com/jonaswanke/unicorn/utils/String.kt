package com.jonaswanke.unicorn.utils

fun String.removePrefix(prefix: CharSequence, ignoreCase: Boolean = false): String {
    if (startsWith(prefix, ignoreCase)) {
        return substring(prefix.length)
    }
    return this
}
fun String.removeSuffix(suffix: CharSequence, ignoreCase: Boolean = false): String {
    if (endsWith(suffix, ignoreCase)) {
        return substring(0, length - suffix.length)
    }
    return this
}
