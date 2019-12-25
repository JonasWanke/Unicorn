package com.jonaswanke.unicorn.api

import net.swiftzer.semver.SemVer

fun SemVer.Companion.parseOrNull(version: String): SemVer? {
    return try {
        parse(version)
    } catch (e: IllegalArgumentException) {
        null
    }
}
