package com.jonaswanke.unicorn.utils

import java.io.File

fun File.withoutExtension(extension: String): File {
    return if (this.extension != extension) this
    else File(parentFile, nameWithoutExtension)
}

fun File.isDescendantOf(base: File): Boolean {
    return base.resolve(this).startsWith(base)
}

fun File.resolveTo(base: File): File = base.resolve(this)
