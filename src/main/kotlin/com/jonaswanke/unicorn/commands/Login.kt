package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.jonaswanke.unicorn.script.Unicorn.globalConfig

open class Login : BaseCommand() {
    private val username by argument("username").optional()
    private val endpoint by option("-e", "--endpoint")

    override fun run() {
        super.run()

        githubAuthenticate(true, username = username, endpoint = endpoint)
    }
}

open class Logout : BaseCommand() {
    override fun run() {
        globalConfig = globalConfig.copy(github = null)
    }
}
