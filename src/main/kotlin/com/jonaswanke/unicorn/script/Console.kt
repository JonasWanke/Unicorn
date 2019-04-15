package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.unicorn.script.command.UnicornCommand

fun UnicornCommand.echo(message: Any?, trailingNewline: Boolean = true, err: Boolean = false) {
    TermUi.echo(message, trailingNewline, err, context.console)
}
