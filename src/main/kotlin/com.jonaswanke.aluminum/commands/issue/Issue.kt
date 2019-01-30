package com.jonaswanke.aluminum.commands.issue

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.aluminum.commands.BaseCommand
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class Issue : BaseCommand(name = "issue") {
    init {
        subcommands(
            AssignIssue()
        )
    }

    override fun run() = Unit
}
