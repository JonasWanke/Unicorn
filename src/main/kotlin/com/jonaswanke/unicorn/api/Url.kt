package com.jonaswanke.unicorn.api

import com.github.ajalt.clikt.core.BadParameterValue
import com.jonaswanke.unicorn.core.InteractiveRunContext
import java.net.MalformedURLException
import java.net.URL

fun InteractiveRunContext.promptUrl(
    text: String,
    default: String? = null,
    showDefault: Boolean = true,
    promptSuffix: String = ": "
): URL = prompt(text, default, showDefault, promptSuffix, convert = ::parseUrlOrThrow)

fun InteractiveRunContext.promptOptionalUrl(
    text: String,
    optionalText: String = " (optional)",
    promptSuffix: String = ": "
): URL? = promptOptional(text, optionalText, promptSuffix, convert = ::parseUrlOrThrow)

private fun parseUrlOrThrow(raw: String): URL {
    val url = raw.trim()
    return try {
        URL(url)
    } catch (e: MalformedURLException) {
        if (e.message?.contains("no protocol") == true)
            return parseUrlOrThrow("https://$raw")
        throw BadParameterValue(url)
    }
}
