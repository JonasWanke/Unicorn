package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.CliktCommand
import kotlin.jvm.javaClass
import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.Unicorn.globalConfig
import com.jonaswanke.unicorn.script.command.UnicornCommand
import com.jonaswanke.unicorn.utils.OAuthCredentialsProvider
import com.jonaswanke.unicorn.utils.TextIoConsoleWrapper
import com.jonaswanke.unicorn.utils.prompt
import com.jonaswanke.unicorn.utils.promptOptional
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.GitHub
import java.io.File


abstract class BaseCommand(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false
) : CliktCommand(help, epilog, name, invokeWithoutSubcommand) {
    init {
        context {
            console = TextIoConsoleWrapper
        }
    }

    open val prefix by option("--prefix")
        .file(exists = true, fileOkay = false)
        .default(File(System.getProperty("user.dir")))


    // region GitHub
    private var githubAuthResult: GithubAuthResult? = null

    private fun getGithubAuthResult(): GithubAuthResult {
        if (githubAuthResult == null)
            githubAuthenticate()
        return githubAuthResult!!
    }

    fun githubAuthenticate(
        forceNew: Boolean = false,
        username: String? = null,
        token: String? = null,
        endpoint: String? = null
    ) {
        fun buildAuthResult(config: GlobalConfig.GithubConfig, github: GitHub): GithubAuthResult {
            return GithubAuthResult(github, OAuthCredentialsProvider(config.oauthToken))
        }

        if (!forceNew) {
            globalConfig.github?.also { githubConfig ->
                val github = githubConfig.buildGitHubApi()
                if (github.isCredentialValid) {
                    githubAuthResult = buildAuthResult(githubConfig, github)
                    return
                }
                echo("The stored GitHub credentials are invalid")
            }
        }

        echo("Please enter your GitHub credentials (They will be stored unencrypted in the installation directory):")
        while (true) {
            val usernameAct = username
                ?: prompt("GitHub username")
                ?: throw MissingParameter("username")
            val tokenAct = token
                ?: prompt("Personal access token (with repo scope)", hideInput = true)
                ?: throw MissingParameter("personal access token")
            val endpointAct = endpoint
                ?: promptOptional("Custom GitHub endpoint?")

            val githubConfig = GlobalConfig.GithubConfig(
                username = usernameAct, oauthToken = tokenAct, endpoint = endpointAct
            )
            globalConfig = globalConfig.copy(github = githubConfig)
            val github = githubConfig.buildGitHubApi()

            val isValid = github.isCredentialValid
            if (isValid) {
                echo("Login successful")
                githubAuthResult = buildAuthResult(githubConfig, github)
                return
            } else
                echo("Your credentials are invalid. Please try again.")
        }
    }

    data class GithubAuthResult(val github: GitHub, val credentialsProvider: CredentialsProvider)

    val github get() = getGithubAuthResult().github
    // endregion

    override fun run() {
        Unicorn.prefix = prefix
    }
}
