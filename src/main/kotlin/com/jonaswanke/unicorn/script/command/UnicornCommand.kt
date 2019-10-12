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
    help: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, help, builder)
    commands(command)
}

fun Unicorn.executableCommand(
    name: String,
    help: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, help, builder)
    commands(command)
}


fun UnicornCommand.command(
    name: String,
    help: String = "",
    builder: CommandBuilder
) {
    val command = createCommand(name, help, builder)
    subcommands(command)
}

fun UnicornCommand.executableCommand(
    name: String,
    help: String = "",
    builder: ExecutableCommandBuilder
) {
    val command = createExecutableCommand(name, help, builder)
    subcommands(command)
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
    }.apply { runnable }
}
