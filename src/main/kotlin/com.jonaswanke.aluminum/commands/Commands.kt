package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.aluminum.commands.issue.Issue
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