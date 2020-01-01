package com.jonaswanke.unicorn.api

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths

class Glob(pattern: String) {
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

    fun matches(file: File): Boolean = matches(file.path)
    fun matches(file: String): Boolean {
        return matcher.matches(Paths.get(file))
    }
}
