package com.jonaswanke.unicorn.api

import com.github.ajalt.clikt.core.BadParameterValue
import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.script.parameters.*
import net.swiftzer.semver.SemVer

val SemVer.isInitialDevelopment: Boolean
    get() = major == 0

val SemVer.nextMajor: SemVer
    get() = SemVer(major + 1)
val SemVer.nextMinor: SemVer
    get() = SemVer(major, minor + 1)
val SemVer.nextPatch: SemVer
    get() = SemVer(major, minor, patch + 1)

val SemVer.nextBreaking: SemVer
    get() = if (isInitialDevelopment) nextMinor else nextMajor
val SemVer.nextFeature: SemVer
    get() = if (isInitialDevelopment) nextPatch else nextMinor
val SemVer.nextFix: SemVer
    get() = nextPatch

fun SemVer.Companion.parseOrNull(version: String): SemVer? {
    return try {
        parse(version)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun InteractiveRunContext.promptSemVer(
    text: String,
    default: String? = null,
    showDefault: Boolean = true,
    promptSuffix: String = ": "
): SemVer = prompt(text, default, showDefault, promptSuffix, convert = ::parseSemVerOrThrow)

fun InteractiveRunContext.promptOptionalSemVer(
    text: String,
    optionalText: String = " (optional)",
    promptSuffix: String = ": "
): SemVer? = promptOptional(text, optionalText, promptSuffix, convert = ::parseSemVerOrThrow)

fun UnicornRawArgument.semVer(): UnicornProcessedArgument<SemVer, SemVer> = convert { parseSemVerOrThrow(it) }
fun UnicornRawOption.semVer(): UnicornNullableOption<SemVer, SemVer> = convert { parseSemVerOrThrow(it) }

private fun parseSemVerOrThrow(raw: String): SemVer {
    val normalized = raw.trim().removePrefix("v")
    return try {
        SemVer.parse(normalized)
    } catch (e: IllegalArgumentException) {
        throw BadParameterValue(normalized)
    }
}
