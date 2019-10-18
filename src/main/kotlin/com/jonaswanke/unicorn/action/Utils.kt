package com.jonaswanke.unicorn.action

import com.github.ajalt.clikt.core.CliktError
import java.io.File

internal fun printWarning(message: String, file: File? = null, line: Int? = null, col: Int? = null) {
    //: :warning file={name},line={line},col={col}::{message}
    println(buildString {
        append("::warning ")
        append(listOfNotNull(
            file?.let { "file=${file.path}" },
            line?.let { "line=$line" },
            col?.let { "col=$col" }
        ).joinToString(","))
        append("::$message")
    })
}

internal fun printError(message: String, file: File? = null, line: Int? = null, col: Int? = null) {
    // ::error file={name},line={line},col={col}::{message}
    println(buildString {
        append("::error ")
        append(listOfNotNull(
            file?.let { "file=${file.path}" },
            line?.let { "line=$line" },
            col?.let { "col=$col" }
        ).joinToString(","))
        append("::$message")
    })
}

internal fun throwError(message: String, file: File? = null, line: Int? = null, col: Int? = null): Nothing {
    printError(message, file, line, col)
    throw CliktError(message)
}
