package com.jonaswanke.unicorn.script.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.UnicornMarker
import java.io.File

@UnicornMarker
abstract class UnicornCommand(name: String, help: String = "", invokeWithoutSubcommand: Boolean = false) :
    CliktCommand(help, name = name, invokeWithoutSubcommand = invokeWithoutSubcommand) {
    val prefix by option("--prefix")
        .file(exists = true, fileOkay = false)
        .default(File(System.getProperty("user.dir")))

    private val aliases = mutableMapOf<String, String>()
    override fun aliases() = aliases.mapValues { listOf(it.value) }

    fun addSubcommand(command: UnicornCommand, aliases: List<String> = emptyList()) {
        subcommands(command)
        this.aliases += aliases.associateWith { command.commandName }
    }

    protected fun beforeRun() {
        Unicorn.prefix = prefix
    }

    override fun run() {
        beforeRun()
    }
}


typealias CommandBuilder = UnicornCommand.() -> Unit
typealias ExecutableCommandBuilder = UnicornCommand.() -> (() -> Unit)

fun Unicorn.command(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, help, builder)
    addCommand(command, aliases)
}

fun Unicorn.executableCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, help, builder)
    addCommand(command, aliases)
}


fun UnicornCommand.command(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, help, builder)
    addSubcommand(command, aliases)
}

fun UnicornCommand.executableCommand(
    name: String,
    aliases: List<String> = emptyList(),
    help: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, help, builder)
    addSubcommand(command, aliases)
}


private fun createCommand(
    name: String,
    help: String = "",
    builder: CommandBuilder
): UnicornCommand {
    return object : UnicornCommand(name, help, false) {}
        .apply { builder() }
}

private fun createExecutableCommand(
    name: String,
    help: String = "",
    builder: ExecutableCommandBuilder
): UnicornCommand {
    return object : UnicornCommand(name, help, true) {
        val runnable = builder()

        override fun run() {
            super.run()
            runnable()
        }
    }
}
