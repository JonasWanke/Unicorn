package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.utils.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import kotlin.contracts.ExperimentalContracts


@ExperimentalContracts
abstract class BaseCommand(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false
) : CliktCommand(help, epilog, name, invokeWithoutSubcommand) {
    companion object {
        private const val CONFIG_GLOBAL_FILE = "config.yml"
        private const val CONFIG_PROJECT_FILE = ".unicorn.yml"
    }

    init {
        context {
            console = TextIoConsoleWrapper
        }
    }

    val prefix by option("--prefix")
        .file(exists = true, fileOkay = false)
        .default(File(System.getProperty("user.dir")))

    // region Global config
    private val installDir = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile.parentFile
    private val globalConfigFile: File
        get() = File(installDir, CONFIG_GLOBAL_FILE).apply {
            if (!exists()) {
                createNewFile()
                globalConfig = GlobalConfig(github = null)
            }
        }
    var globalConfig: GlobalConfig
        get() = globalConfigFile.inputStream().readConfig()
        set(value) = globalConfigFile.outputStream().writeConfig(value)
    // endregion

    // region Project config
    fun getProjectConfig(dir: File = prefix): ProjectConfig {
        return File(dir, CONFIG_PROJECT_FILE).inputStream().readConfig()
    }

    fun setProjectConfig(config: ProjectConfig, dir: File = prefix) {
        File(dir, CONFIG_PROJECT_FILE).outputStream().writeConfig(config)
    }
    // endregion


    // region Git
    val git get() = Git.open(prefix)
    // endregion

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
        password: String? = null,
        token: String? = null,
        endpoint: String? = null
    ) {
        fun buildAuthResult(config: GlobalConfig.GithubConfig, github: GitHub): GithubAuthResult {
            return GithubAuthResult(github, OAuthCredentialsProvider(config.oauthToken ?: ""))
        }

        if (!forceNew) {
            globalConfig.github?.also { githubConfig ->
                val github = githubConfig.buildGithub()
                if (github.isCredentialValid) {
                    githubAuthResult = buildAuthResult(githubConfig, github)
                    return
                }
                echo("The stored GitHub credentials are invalid")
            }
        }

        while (true) {
            echo("Please enter your GitHub credentials (They will be stored unencrypted in the installation directory):")
            val usernameAct = username
                ?: prompt("GitHub username")
                ?: throw MissingParameter("username")
            val (passwordAct, tokenAct) =
                if (confirm(
                        "Use password (alternative: OAuth-token) (When 2FA is enabled, only OAuth will work)",
                        default = true
                    ) != false
                ) {
                    val passwordAct = password
                        ?: prompt("Password", hideInput = true)
                        ?: throw MissingParameter("password")
                    passwordAct to null
                } else {
                    val tokenAct = token
                        ?: prompt("Personal access token", hideInput = true)
                        ?: throw MissingParameter("personal access token")
                    null to tokenAct
                }
            val endpointAct = endpoint
                ?: promptOptional("Custom GitHub endpoint?")

            val githubConfig = GlobalConfig.GithubConfig(
                username = usernameAct, password = passwordAct, oauthToken = tokenAct, endpoint = endpointAct
            )
            globalConfig = globalConfig.copy(github = githubConfig)
            val github = githubConfig.buildGithub()

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
    val githubCp get() = getGithubAuthResult().credentialsProvider

    val githubRepoName: String?
        get() {
            return call(git.remoteList()).mapNotNull { remoteConfig ->
                remoteConfig.urIs.firstOrNull { it.host == "github.com" }
            }
                .firstOrNull()
                ?.path
                ?.trimStart('/')
                ?.substringBeforeLast('.')
        }
    val githubRepo: GHRepository?
        get() = githubRepoName?.let { github.getRepository(it) }

    fun requireGithubRepo(): GHRepository {
        return githubRepo
            ?: throw UsageError("No repository is configured for the current project")
    }
    // endregion
}
