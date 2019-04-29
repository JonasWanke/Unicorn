package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.CliktConsole
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.output.defaultCliktConsole
import java.io.IOError


fun promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = defaultCliktConsole(),
    convert: ((String?) -> String?) = { it }
): String? {
    return promptOptional<String?>(
        text,
        default,
        optionalText,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        promptSuffix,
        showDefault,
        console,
        convert
    )
}

fun <T> promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = defaultCliktConsole(),
    convert: ((String?) -> T?)
): T? {
    // Original source: TermUi.prompt
    val prompt = buildString {
        append(text)
        append(optionalText)
        if (!default.isNullOrBlank() && showDefault)
            append(" [").append(default).append("]")
        append(promptSuffix)
    }

    try {
        while (true) {
            val value = console.promptForLine(prompt, hideInput)?.let {
                if (it.isBlank()) null else it
            }
            val result = try {
                convert.invoke(value)
            } catch (err: UsageError) {
                TermUi.echo(err.helpMessage(null), console = console)
                continue
            }

            if (!requireConfirmation) return result

            var value2: String?
            while (true) {
                value2 = console.promptForLine(confirmationPrompt, hideInput)
                // No need to convert the confirmation, since it is valid if it matches the
                // first value.
                if (!value2.isNullOrBlank()) break
            }
            if (value == value2) return result
            TermUi.echo("Error: the two entered values do not match", console = console)
        }
    } catch (err: IOError) {
        return null
    }
}
