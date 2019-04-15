package com.jonaswanke.unicorn.commands.issue

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.commands.BaseCommand

class Issue : BaseCommand(name = "issue") {
    init {
        subcommands(
            AssignIssue(), CompleteIssue()
        )
    }

    override fun run() = Unit
}
