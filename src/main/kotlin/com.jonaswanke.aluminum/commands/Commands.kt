package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

object Commands : CliktCommand() {
    init {
        subcommands(Init())
    }

    override fun run() = Unit
}
