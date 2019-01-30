package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.commands.issue.Issue
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create(),
            Issue()
        )
    }

    override fun run() = Unit
}