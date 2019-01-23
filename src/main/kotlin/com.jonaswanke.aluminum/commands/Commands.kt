package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

object Commands : BaseCommand() {
    init {
        subcommands(Create())
    }

    override fun run() = Unit
}

abstract class BaseCommand : CliktCommand() {
    init {
        context {
            console = TextIoConsoleWrapper
        }
    }
}
