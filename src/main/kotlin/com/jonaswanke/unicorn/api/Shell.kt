package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.buildMarkup
import com.jonaswanke.unicorn.utils.code
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.util.concurrent.TimeUnit

const val EXIT_VALUE_DEFAULT = 0

fun RunContext.execute(
    program: String,
    vararg arguments: String,
    directory: File? = projectDir,
    timeout: Long? = 10,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): Unit = group(buildMarkup {
    +"Executing "
    code("$program ${arguments.joinToString(" ")}")
    if (directory != null) +"in directory $directory"
}) {
    val file = findProgram(program, directory) ?: exit("Cannot find the program $program")
    log.d("Found executable: ${file.absolutePath}")

    val process = ProcessBuilder(file.absolutePath, *arguments)
        .directory(directory)
        .start()
        .apply {
            if (timeout == null) return@apply

            if (timeout < 0) waitFor()
            else waitFor(timeout, timeoutUnit)
        }

    process.inputStream.bufferedReader().forEachLine {
        log.i(it)
    }

    if (process.exitValue() != EXIT_VALUE_DEFAULT) {
        log.w("Exit code: ${process.exitValue()}")
        process.errorStream.bufferedReader().forEachLine {
            log.w(it)
        }
    }
}

private fun findProgram(program: String, currentDir: File?): File? {
    val initial = listOfNotNull(currentDir)
    return (initial + System.getenv("PATH").split(';').map { File(it) })
        .flatMap {
            listOfNotNull(
                it.resolve(program),
                if (SystemUtils.IS_OS_WINDOWS) it.resolve("$program.bat") else null
            )
        }
        .firstOrNull { it.exists() && it.canExecute() }
}
