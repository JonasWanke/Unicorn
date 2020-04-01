package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.api.gitHub
import com.jonaswanke.unicorn.core.GlobalConfig
import com.jonaswanke.unicorn.core.LogCollector
import com.jonaswanke.unicorn.core.LogCollector.Priority
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.Markup
import org.kohsuke.github.GHRepository
import java.io.File

class GitHubActionRunContext private constructor(
    override val log: LogCollector
) : RunContext() {
    constructor() : this(GitHubActionLogCollector())

    override val environment = Environment.GITHUB_ACTION

    override var globalConfig: GlobalConfig
        get() {
            val config = super.globalConfig
            return config.copy(
                gitHub = (config.gitHub ?: GlobalConfig.GitHubConfig()).copy(
                    anonymousToken = Action.getRequiredInput("repo-token")
                )
            )
        }
        set(value) {
            super.globalConfig = value
        }

    override val projectDir = Action.Env.githubWorkspace

    override fun copyWithGroup(group: LogCollector.Group) = GitHubActionRunContext(group)

    val event: Action.Event = Action.Event.detect(this)

    val gitHubRepo: GHRepository
        get() = gitHub.api.getRepository(Action.Env.githubRepository)
}

class GitHubActionLogCollector : LogCollector {
    override fun log(
        priority: Priority,
        markup: Markup,
        groups: List<LogCollector.Group>,
        file: File?,
        line: Int?,
        col: Int?
    ) {
        val message = (groups.map { it.name } + markup).joinToString(": ") { it.toConsoleString() }
        fun logCommand(command: String) {
            val metadata = mapOf(
                "file" to file?.path,
                "line" to line?.toString(),
                "col" to col?.toString()
            )
                .mapNotNull { (key, value) -> value?.let { "$key=${it.metaValueEscaped}" } }
                .takeUnless { it.isEmpty() }
                ?.joinToString(",")
                ?.let { " $it" }
                ?: ""

            println("::$command $metadata::${message.dataEscaped}")
        }

        when (priority) {
            Priority.DEBUG -> logCommand("debug")
            Priority.INFO -> println(message)
            Priority.WARNING -> logCommand("warning")
            Priority.ERROR, Priority.WTF -> logCommand("error")
        }
    }

    private val String.dataEscaped: String
        get() = replace("\r", "%0D")
            .replace("\n", "%0A")

    private val String.metaValueEscaped: String
        get() = dataEscaped
            .replace("]", "%5D")
            .replace(";", "%3B")
}
