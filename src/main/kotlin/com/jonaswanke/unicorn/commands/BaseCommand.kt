package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jonaswanke.unicorn.console.ConsoleRunContext
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.TextIoConsoleWrapper
import java.io.File


abstract class BaseCommand(
    name: String? = null,
    val aliases: List<String> = emptyList(),
    help: String = "",
    epilog: String = "",
    invokeWithoutSubcommand: Boolean = false
) : CliktCommand(help, epilog, name, invokeWithoutSubcommand) {
    init {
        context {
            console = TextIoConsoleWrapper
        }
    }

    private val subcommands = mutableListOf<BaseCommand>()
    override fun aliases() = subcommands.flatMap { command ->
        command.aliases.map { it to listOf(command.commandName) }
    }.toMap()

    fun addSubcommand(command: BaseCommand) {
        subcommands(command)
        subcommands += command
    }

    open val prefix by option("--prefix")
        .file(exists = true, fileOkay = false)
        .default(File(System.getProperty("user.dir")))

    final override fun run() {
        val runContext = ConsoleRunContext(prefix, context.console)
        execute(runContext)
        echo("Done!")
    }

    open fun execute(context: RunContext) {}
}
