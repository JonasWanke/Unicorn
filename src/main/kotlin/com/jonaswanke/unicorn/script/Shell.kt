package com.jonaswanke.unicorn.script

import java.io.File
import java.util.concurrent.TimeUnit

const val EXIT_VALUE_DEFAULT = 0

fun execute(
    vararg arguments: String,
    directory: File? = null,
    timeout: Long? = 10,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String {
    val process = ProcessBuilder(*arguments)
        .directory(directory)
        .start()
        .apply {
            if (timeout == null) return@apply

            if (timeout < 0) waitFor()
            else waitFor(timeout, timeoutUnit)
        }

    if (process.exitValue() != EXIT_VALUE_DEFAULT)
        throw Exception(process.errorStream.bufferedReader().readText())
    return process.inputStream.bufferedReader().readText()
}
