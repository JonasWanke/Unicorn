package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.commands.Commands
import com.jonaswanke.unicorn.script.command.UnicornCommand

@DslMarker
annotation class UnicornMarker


@UnicornMarker
object Unicorn {
    private val command = Commands

    internal fun commands(vararg commands: UnicornCommand) {
        command.subcommands(*commands)
    }

    internal fun main(argv: List<String>) {
        command.main(argv)
    }
}

fun unicorn(unicornBuilder: Unicorn.() -> Unit) {
    Unicorn.unicornBuilder()
}
