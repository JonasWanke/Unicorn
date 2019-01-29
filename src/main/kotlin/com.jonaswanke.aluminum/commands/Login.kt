package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
open class Login : BaseCommand() {
    private val username by argument("username").optional()
    private val endpoint by option("-e", "--endpoint")

    override fun run() {
        githubAuthenticate(true, username = username, endpoint = endpoint)
    }
}
