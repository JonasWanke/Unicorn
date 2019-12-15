package com.jonaswanke.unicorn.script

import com.jonaswanke.unicorn.commands.Commands
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.script.command.UnicornCommand
import java.io.File

@DslMarker
annotation class UnicornMarker


@UnicornMarker
object Unicorn {
    lateinit var prefix: File
        internal set

    val context: RunContext = TODO()

    // region Commands
    internal fun main(argv: List<String>) {
        Commands.main(argv)
    }

    internal fun addCommand(command: UnicornCommand) {
        Commands.addSubcommand(command)
    }
    // endregion
}

fun unicorn(unicornBuilder: Unicorn.() -> Unit) {
    Unicorn.unicornBuilder()
}
