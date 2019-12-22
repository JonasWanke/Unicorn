package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.option
import com.jonaswanke.unicorn.script.parameters.optional

fun Unicorn.registerLoginLogoutCommands() {
    command("login") {
        run(
            argument("username").optional(),
            option("-e", "--endpoint")
        ) { username, endpoint ->
            GitHub.authenticate(this, true, username = username, endpoint = endpoint)
        }
    }

    command("logout") {
        run {
            globalConfig = globalConfig.copy(gitHub = null)
        }
    }
}
