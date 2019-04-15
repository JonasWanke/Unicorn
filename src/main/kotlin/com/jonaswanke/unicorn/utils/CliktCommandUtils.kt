package com.jonaswanke.unicorn.utils

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.CliktConsole
import com.github.ajalt.clikt.output.TermUi
import java.io.IOError

fun CliktCommand.editText(
    text: String, editor: String? = null, env: Map<String, String> = emptyMap(),
    requireSave: Boolean = false, extension: String = ".txt"
): String? {
    return TermUi.editText(text, editor, env, requireSave, extension)
}

fun CliktCommand.editFile(
    filename: String, editor: String? = null, env: Map<String, String> = emptyMap(),
    requireSave: Boolean = false, extension: String = ".txt"
) {
    TermUi.editFile(filename, editor, env, requireSave, extension)
}


fun CliktCommand.newLine() {
    TermUi.echo("", trailingNewline = true, console = context.console)
}

fun CliktCommand.prompt(
    text: String,
    default: String? = null,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> String) = { it }
): String {
    return prompt<String>(
        text,
        default,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        promptSuffix,
        showDefault,
        console,
        convert
    )
}

fun <T> CliktCommand.prompt(
    text: String,
    default: String? = null,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> T?)
): T {
    // Original source: TermUi.prompt
    val prompt = buildString {
        append(text)
        if (!default.isNullOrBlank() && showDefault)
            append(" [").append(default).append("]")
        append(promptSuffix)
    }

    while (true) {
        var value: String
        while (true) {
            val currentValue = console.promptForLine(prompt, hideInput)

            if (!currentValue.isNullOrBlank()) {
                value = currentValue
                break
                // Skip confirmation prompt if default is used
            } else if (default != null)
                convert.invoke(default)?.let { return it }
        }
        val result = try {
            convert.invoke(value)
        } catch (err: UsageError) {
            TermUi.echo(err.helpMessage(null), console = console)
            continue
        } ?: continue

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
}

fun CliktCommand.promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
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

fun <T> CliktCommand.promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
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

fun CliktCommand.confirm(
    text: String,
    default: Boolean = false,
    abort: Boolean = false,
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console
): Boolean? {
    return TermUi.confirm(text, default, abort, promptSuffix, showDefault, console)
}
