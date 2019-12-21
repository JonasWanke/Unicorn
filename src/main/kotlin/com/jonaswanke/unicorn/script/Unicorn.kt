package com.jonaswanke.unicorn.script

import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.commands.Commands
import com.jonaswanke.unicorn.commands.registerIssueCommands

@DslMarker
annotation class UnicornMarker


@UnicornMarker
object Unicorn {
    // region Commands
    internal fun main(argv: List<String>) {
        registerIssueCommands()
        Commands.main(argv)
    }

    internal fun addCommand(command: BaseCommand) {
        Commands.addSubcommand(command)
    }
    // endregion
}

fun unicorn(unicornBuilder: Unicorn.() -> Unit) {
    Unicorn.unicornBuilder()
}
