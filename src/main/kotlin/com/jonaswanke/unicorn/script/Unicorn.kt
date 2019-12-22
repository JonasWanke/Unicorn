package com.jonaswanke.unicorn.script

import com.jonaswanke.unicorn.commands.*

@DslMarker
annotation class UnicornMarker


@UnicornMarker
object Unicorn {
    internal fun main(argv: List<String>) {
        registerLoginLogoutCommands()
        registerIssueCommands()
        registerLabelCommands()
        Commands.main(argv)
    }

    internal fun addCommand(command: BaseCommand) {
        Commands.addSubcommand(command)
    }
}

fun unicorn(unicornBuilder: Unicorn.() -> Unit) {
    Unicorn.unicornBuilder()
}
