package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import java.io.File
import java.util.concurrent.TimeUnit

const val EXIT_VALUE_DEFAULT = 0

fun execute(
    context: RunContext,
    vararg arguments: String,
    directory: File? = null,
    timeout: Long? = 10,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String {
    context.log.i {
        +"Executing \"${arguments.joinToString(" ")}\""
        if (directory != null) +"in directory $directory"
    }
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
