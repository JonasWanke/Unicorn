package com.jonaswanke.unicorn.script.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.UnicornMarker

@UnicornMarker
abstract class UnicornCommand(name: String, help: String = "", invokeWithoutSubcommand: Boolean = false) :
    CliktCommand(help, name = name, invokeWithoutSubcommand = invokeWithoutSubcommand)


typealias CommandBuilder = UnicornCommand.() -> (() -> Unit)

fun Unicorn.command(
    name: String,
    help: String = "",
    invokeWithoutSubcommand: Boolean = false,
    builder: CommandBuilder
) {
    val command = createCommand(name, help, invokeWithoutSubcommand, builder)
    commands(command)
}

fun UnicornCommand.command(
    name: String,
    help: String = "",
    invokeWithoutSubcommand: Boolean = false,
    builder: CommandBuilder
) {
    val command = createCommand(name, help, invokeWithoutSubcommand, builder)
    subcommands(command)
}

private fun createCommand(
    name: String,
    help: String = "",
    invokeWithoutSubcommand: Boolean = false,
    builder: CommandBuilder
): UnicornCommand {
    return object : UnicornCommand(name, help, invokeWithoutSubcommand) {
        val runnable = builder()

        override fun run() {
            runnable()
        }
    }.apply { runnable }
}
