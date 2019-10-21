package com.jonaswanke.unicorn.script.command

import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.UnicornMarker

@UnicornMarker
abstract class UnicornCommand(
    name: String? = null,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    invokeWithoutSubcommand: Boolean = false
) : BaseCommand(name, aliases, help, epilog, invokeWithoutSubcommand)


typealias CommandBuilder = UnicornCommand.() -> Unit
typealias ExecutableCommandBuilder = UnicornCommand.() -> (() -> Unit)

fun Unicorn.command(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, aliases, help, epilog, builder)
    addCommand(command)
}

fun Unicorn.executableCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, aliases, help, epilog, builder)
    addCommand(command)
}


fun UnicornCommand.command(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, aliases, help, epilog, builder)
    addSubcommand(command)
}

fun UnicornCommand.executableCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, aliases, help, epilog, builder)
    addSubcommand(command)
}


private fun createCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: CommandBuilder
): UnicornCommand {
    return object : UnicornCommand(name, aliases, help, epilog, false) {}
        .apply { builder() }
}

private fun createExecutableCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    builder: ExecutableCommandBuilder
): UnicornCommand {
    return object : UnicornCommand(name, aliases, help, epilog, true) {
        val runnable = builder()

        override fun execute() = runnable()
    }
}
