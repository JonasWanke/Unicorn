package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.option
import com.jonaswanke.unicorn.script.parameters.optional

fun Unicorn.registerLoginLogoutCommands() {
    command("login") {
        help = "Login to GitHub"

        run(
            argument("username", help = "Your GitHub username")
                .optional(),
            option("-e", "--endpoint", help = "Custom GitHub endpoint (when using GitHub Enterprise)")
        ) { username, endpoint ->
            GitHub.authenticate(this, true, username = username, endpoint = endpoint)
        }
    }

    command("logout") {
        help = "Logout from GitHub (delete stored credentials)"

        run {
            globalConfig = globalConfig.copy(gitHub = null)
        }
    }
}
