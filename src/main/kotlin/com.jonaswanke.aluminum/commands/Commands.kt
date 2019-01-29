package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.aluminum.commands.feature.Feature
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create(),
            Feature()
        )
    }

    override fun run() = Unit
}