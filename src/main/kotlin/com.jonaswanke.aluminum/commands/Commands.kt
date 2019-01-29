package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.CliktConsole
import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.aluminum.GlobalConfig
import com.jonaswanke.aluminum.ProjectConfig
import com.jonaswanke.aluminum.utils.OAuthCredentialsProvider
import com.jonaswanke.aluminum.utils.TextIoConsoleWrapper
import com.jonaswanke.aluminum.utils.readConfig
import com.jonaswanke.aluminum.utils.writeConfig
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.GitHub
import java.io.File
import java.io.IOError
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
object Commands : BaseCommand() {
    init {
        subcommands(Create())
    }

    override fun run() = Unit
}

@ExperimentalContracts
abstract class BaseCommand : CliktCommand() {
    companion object {
        private const val CONFIG_GLOBAL_FILE = ".config.yml"
        private const val CONFIG_PROJECT_FILE = ".aluminum.yml"
    }

    init {
        context {
            console = TextIoConsoleWrapper
        }
    }

    private val installDir = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile
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

    fun getProjectConfig(dir: File = File("")): ProjectConfig {
        return File(dir, CONFIG_PROJECT_FILE).inputStream().readConfig()
    }

    fun setProjectConfig(config: ProjectConfig, dir: File = File("")) {
        File(dir, CONFIG_PROJECT_FILE).outputStream().writeConfig(config)
    }

    protected fun githubAuthenticate(
        forceNew: Boolean = false,
        username: String? = null,
        password: String? = null,
        token: String? = null,
        endpoint: String? = null
    ): GithubAuthResult {
        fun buildAuthResult(config: GlobalConfig.GithubConfig, github: GitHub): GithubAuthResult {
            return GithubAuthResult(github, OAuthCredentialsProvider(config.oauthToken ?: ""))
        }

        if (!forceNew) {
            globalConfig.github?.also { githubConfig ->
                val github = githubConfig.buildGithub()
                if (github.isCredentialValid)
                    return buildAuthResult(githubConfig, github)
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
                return buildAuthResult(githubConfig, github)
            } else
                echo("Your credentials are invalid. Please try again.")
        }
    }

    data class GithubAuthResult(val github: GitHub, val credentialsProvider: CredentialsProvider)
}

fun CliktCommand.editText(
    text: String, editor: String? = null, env: Map<String, String> = emptyMap(),
    requireSave: Boolean = false, extension: String = ".txt"
): String? {
    return TermUi.editText(text, editor, env, requireSave, extension)
}

fun CliktCommand.editFile(
    filename: String, editor: String? = null, env: Map<String, String> = emptyMap(),
    requireSave: Boolean = false, extension: String = ".txt"
) {
    TermUi.editFile(filename, editor, env, requireSave, extension)
}


fun CliktCommand.newLine() {
    TermUi.echo("", trailingNewline = true, console = context.console)
}

@ExperimentalContracts
fun CliktCommand.prompt(
    text: String,
    default: String? = null,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> String) = { it }
): String {
    return prompt<String>(
        text,
        default,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        promptSuffix,
        showDefault,
        console,
        convert
    )
}

@ExperimentalContracts
fun <T> CliktCommand.prompt(
    text: String,
    default: String? = null,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String) -> T?)
): T {
    // Original source: TermUi.prompt
    val prompt = buildString {
        append(text)
        if (!default.isNullOrBlank() && showDefault)
            append(" [").append(default).append("]")
        append(promptSuffix)
    }

    while (true) {
        var value: String
        while (true) {
            val currentValue = console.promptForLine(prompt, hideInput)

            if (!currentValue.isNullOrBlank()) {
                value = currentValue
                break
                // Skip confirmation prompt if default is used
            } else if (default != null)
                convert.invoke(default)?.let { return it }
        }
        val result = try {
            convert.invoke(value)
        } catch (err: UsageError) {
            TermUi.echo(err.helpMessage(null), console = console)
            continue
        } ?: continue

        if (!requireConfirmation) return result

        var value2: String?
        while (true) {
            value2 = console.promptForLine(confirmationPrompt, hideInput)
            // No need to convert the confirmation, since it is valid if it matches the
            // first value.
            if (!value2.isNullOrBlank()) break
        }
        if (value == value2) return result
        TermUi.echo("Error: the two entered values do not match", console = console)
    }
}

@ExperimentalContracts
fun CliktCommand.promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String?) -> String?) = { it }
): String? {
    return promptOptional<String?>(
        text,
        default,
        optionalText,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        promptSuffix,
        showDefault,
        console,
        convert
    )
}

@ExperimentalContracts
fun <T> CliktCommand.promptOptional(
    text: String,
    default: String? = null,
    optionalText: String = " (optional)",
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console,
    convert: ((String?) -> T?)
): T? {
    // Original source: TermUi.prompt
    val prompt = buildString {
        append(text)
        append(optionalText)
        if (!default.isNullOrBlank() && showDefault)
            append(" [").append(default).append("]")
        append(promptSuffix)
    }

    try {
        while (true) {
            val value = console.promptForLine(prompt, hideInput)?.let {
                if (it.isBlank()) null else it
            }
            val result = try {
                convert.invoke(value)
            } catch (err: UsageError) {
                TermUi.echo(err.helpMessage(null), console = console)
                continue
            }

            if (!requireConfirmation) return result

            var value2: String?
            while (true) {
                value2 = console.promptForLine(confirmationPrompt, hideInput)
                // No need to convert the confirmation, since it is valid if it matches the
                // first value.
                if (!value2.isNullOrBlank()) break
            }
            if (value == value2) return result
            TermUi.echo("Error: the two entered values do not match", console = console)
        }
    } catch (err: IOError) {
        return null
    }
}

fun CliktCommand.confirm(
    text: String,
    default: Boolean = false,
    abort: Boolean = false,
    promptSuffix: String = ": ",
    showDefault: Boolean = true,
    console: CliktConsole = context.console
): Boolean? {
    return TermUi.confirm(text, default, abort, promptSuffix, showDefault, console)
}
