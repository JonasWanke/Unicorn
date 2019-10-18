package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.script.command.UnicornCommand

object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create()
        )
    }

    override fun run() = Unit

    private val aliases = mutableMapOf<String, String>()
    override fun aliases() = aliases.mapValues { listOf(it.value) }


    fun addSubcommand(command: UnicornCommand, aliases: List<String> = emptyList()) {
        subcommands(command)
        this.aliases += aliases.associateWith { command.commandName }
    }
}
