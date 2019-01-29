package com.jonaswanke.aluminum.commands.feature

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.aluminum.commands.BaseCommand
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class Feature : BaseCommand() {
    init {
        subcommands(
            AssignFeature()
        )
    }

    override fun run() = Unit
}
