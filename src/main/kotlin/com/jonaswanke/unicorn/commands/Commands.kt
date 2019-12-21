package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.subcommands

object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create()
        )
    }

    override fun execute(context: RunContext) = Unit
}
