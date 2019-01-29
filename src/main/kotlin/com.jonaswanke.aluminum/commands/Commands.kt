package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.subcommands
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create()
        )
    }

    override fun run() = Unit
}