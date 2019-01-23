package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.CliktConsole
import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.aluminum.utils.TextIoConsoleWrapper
import java.io.File
import java.io.IOError
import java.util.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
object Commands : BaseCommand() {
    init {
        subcommands(Create())
    }

    override fun run() = Unit
}

@ExperimentalContracts
abstract class BaseCommand : CliktCommand() {
    init {
        context {
            console = TextIoConsoleWrapper
        }
    }
}

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

@ExperimentalContracts
fun CliktCommand.prompt(
    text: String,
    default: String? = null,
    optional: Boolean = false,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> String) = { it }
): String? {
    return prompt<String>(
        text,
        default,
        optional,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        promptSuffix,
        showDefault,
        console,
        convert
    )
}

@ExperimentalContracts
fun <T> CliktCommand.prompt(
    text: String,
    default: String? = null,
    optional: Boolean = false,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> T)
): T? {
    // Original source: TermUi.prompt
    val prompt = buildString {
        append(text)
        if (optional)
            append(" (optional)")
        if (!default.isNullOrBlank() && showDefault)
            append(" [").append(default).append("]")
        append(promptSuffix)
    }

    try {
        while (true) {
            var value: String
            while (true) {
                value = console.promptForLine(prompt, hideInput) ?: return null

                if (optional || value.isNotBlank()) break
                // Skip confirmation prompt if default is used
                else if (default != null) return convert.invoke(default)
            }
            val result = try {
                convert.invoke(value)
            } catch (err: UsageError) {
                TermUi.echo(err.helpMessage(null), console = console)
                continue
            }

            if (!requireConfirmation) return result

            var value2: String
            while (true) {
                value2 = console.promptForLine(confirmationPrompt, hideInput) ?: return null
                // No need to convert the confirmation, since it is valid if it matches the
                // first value.
                if (value2.isNotEmpty()) break
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
