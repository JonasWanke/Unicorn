package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.core.InteractiveRunContext

object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create()
        )
    }

    override fun execute(context: InteractiveRunContext) = Unit
}
