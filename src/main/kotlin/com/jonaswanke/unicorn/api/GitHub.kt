package com.jonaswanke.unicorn.api

import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.UsageError
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.action.Action
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.script.promptOptional
import com.jonaswanke.unicorn.utils.OAuthCredentialsProvider
import com.jonaswanke.unicorn.utils.lazy
import com.jonaswanke.unicorn.utils.list
import com.jonaswanke.unicorn.utils.prompt
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.*
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.file.FileSystems
import java.nio.file.Paths
import org.kohsuke.github.GitHub as ApiGitHub

class GitHub(val api: ApiGitHub, val credentialsProvider: CredentialsProvider) {
    companion object {
        fun authenticateOrNull(context: RunContext): GitHub? {
            val config = context.globalConfig.gitHub ?: return null

            val api = GitHubBuilder().apply {
                if (config.anonymousToken != null)
                    withOAuthToken(config.anonymousToken, "anonymous")
                else if (config.username != null && config.oauthToken != null)
                    withOAuthToken(config.oauthToken, config.username)

                if (config.endpoint != null)
                    withEndpoint(config.endpoint)
            }.build()

            // isCredentialValid tries to retrieve the current user which doesn't work with an anonymous token
            return api.takeIf { config.anonymousToken != null || it.isCredentialValid }
                ?.let {
                    GitHub(api, OAuthCredentialsProvider(config.anonymousToken ?: config.oauthToken!!))
                }
        }

        fun authenticate(
            context: RunContext,
            forceNew: Boolean = false,
            username: String? = null,
            token: String? = null,
            endpoint: String? = null
        ): GitHub = context.group("GitHub login") {
            if (!forceNew)
                authenticateOrNull(this)?.also { return@group it }
            if (!isInteractive) {
                val gitHub = globalConfig.gitHub
                if (gitHub == null)
                    exit("Login failed: No login data available")
                else
                    exit {
                        +"Login failed. credentials used:"
                        list {
                            +"Username: ${gitHub.username}"
                            gitHub.endpoint?.let { +"Custom endpoint: $it" }
                        }
                    }
            }

            i("Please enter your GitHub credentials (They will be stored unencrypted in the installation directory):")
            while (true) {
                val usernameAct = username
                    ?: prompt("GitHub username")
                    ?: throw MissingParameter("username")
                val tokenAct = token
                    ?: prompt("Personal access token (with repo scope)", hideInput = true)
                    ?: throw MissingParameter("personal access token")
                val endpointAct = endpoint
                    ?: promptOptional("Custom GitHub endpoint?")

                val githubConfig = GlobalConfig.GitHubConfig(
                    username = usernameAct, oauthToken = tokenAct, endpoint = endpointAct
                )
                globalConfig = globalConfig.copy(gitHub = githubConfig)

                authenticateOrNull(context)?.let {
                    i("Login successful")
                    return@group it
                }
                w("Your credentials are invalid. Please try again.")
            }

            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Can't be reached")
        }
    }

    fun currentRepoNameOrNull(context: RunContext): String? {
        return Git(context).listRemotes(context).mapNotNull { remoteConfig ->
            remoteConfig.urIs.firstOrNull { it.host == "github.com" }
        }
            .firstOrNull()
            ?.path
            ?.trimStart('/')
            ?.substringBeforeLast('.')
    }

    fun currentRepoOrNull(context: RunContext): GHRepository? {
        return currentRepoNameOrNull(context)?.let { api.getRepository(it) }
    }

    fun currentRepo(context: RunContext): GHRepository {
        return currentRepoOrNull(context)
            ?: throw UsageError("No repository is configured for the current project")
    }
}

val RunContext.gitHub: GitHub by lazy { GitHub.authenticate(this) }
val RunContext.gitHubRepo: GHRepository by lazy { gitHub.currentRepo(this) }

// region Issue
fun String.toIssueId(context: RunContext): Int {
    val trimmed = if (startsWith("#")) substring(1) else this
    return trimmed.toIntOrNull()
        ?: context.exit("Couldn't parse issue id \"$trimmed\"")
}

fun GHIssue.assignTo(context: RunContext, user: GHUser, throwIfAlreadyAssigned: Boolean = false): List<GHUser> =
    context.group("Assign GitHub issue #$number to ${user.login}") {
        val oldAssignees = assignees
        d("Current assignees: ${oldAssignees.joinToString { it.login }}")

        if (throwIfAlreadyAssigned && (oldAssignees.size > 1 || (oldAssignees.size == 1 && oldAssignees.first() != user)))
            exit("Issue is already assigned")

        assignTo(user)
        i("Successfully assigned")

        oldAssignees
    }


fun ConventionalCommit.Companion.format(
    context: RunContext,
    issue: GHIssue,
    description: String
): String? {
    val type = issue.getType(context) ?: return null
    return format(type, issue.getComponents(context), description)
}

fun GHIssue.openPullRequest(
    context: RunContext,
    title: String,
    git: Git = context.git,
    assignees: List<String> = listOf(GitHub.authenticate(context).api.myself.login),
    labels: List<String> = getLabels().map { it.name },
    milestone: String? = this.milestone.title,
    base: String? = null
) {
    fun String.encode() = URLEncoder.encode(this, "UTF-8")
    fun List<String>.encode() = joinToString(",") { it.encode() }

    val link = buildString {
        append("${repository.htmlUrl}/compare/")
        if (base != null)
            append("${base.encode()}..")
        append(git.currentBranchName.encode())
        append("?expand=1")
        append("&title=${title.encode()}")
        append("&assignees=${assignees.encode()}")
        append("&labels=${labels.encode()}")
        if (milestone != null)
            append("&milestone=${milestone.encode()}")
    }
    Desktop.getDesktop().browse(URI(link))
}
// endregion

// region Pull Request
private val PR_ISSUES_REGEX =
    "(?:closed|closes|close|fixed|fixes|fix|resolved|resolves|resolve):?\\s*(#\\d+\\s*(?:,\\s*#\\d+\\s*)*)"
        .toRegex(RegexOption.IGNORE_CASE)
val GHPullRequest.closedIssues: List<GHIssue>
    get() = PR_ISSUES_REGEX.findAll(body)
        .flatMap { result ->
            result.groups[1]!!.value.splitToSequence(',')
        }
        .map { issueIdString ->
            issueIdString.takeUnless { it.isBlank() }
                ?.trim()
                ?.removePrefix("#")
                ?.toInt()
        }
        .filterNotNull()
        .map { issueId ->
            repository.getIssue(issueId)
        }
        .toList()

fun GHPullRequest.toCommitMessage() = buildString {
    val commit = ConventionalCommit.parse(title)
    append(commit.description)

    val issues = closedIssues
    if (issues.isNotEmpty())
        append(closedIssues.joinToString(prefix = ", fixes ") { "[#${it.number}](${it.htmlUrl})" })
}
// endregion

// region Label
fun GHIssue.setLabels(labels: List<Label>, group: LabelGroup) {
    labels.forEach { require(it in group) }

    // Remove labels from group that are no longer wanted
    this.labels
        .filter { it.name.startsWith(group.prefix) }
        .filter { existing -> labels.none { it.name == existing.name } }
        .takeUnless { it.isEmpty() }
        ?.let {
            removeLabels(it)
            if (this is GHPullRequest) refresh()
            else Action.printWarning("Updating issue labels is buggy ATM")
        }

    // Add new labels
    labels.map { it.get(repository) }
        .filter { new -> this.labels.none { it.name == new.name } }
        .takeUnless { it.isEmpty() }
        ?.let {
            addLabels(it)
            if (this is GHPullRequest) refresh()
            else Action.printWarning("Updating issue labels is buggy ATM")
        }
}

fun GHIssue.getLabels(group: LabelGroup): List<Label> =
    labels.filter { it.name.startsWith(group.prefix) }
        .mapNotNull {
            group[it.name] ?: {
                println("Unknown label \"${it.name}\" with known prefix \"${group.prefix}\"")
                null
            }()
        }

fun GHIssue.getType(context: RunContext): String? {
    if (this is GHPullRequest)
        ConventionalCommit.tryParse(title)
            ?.takeIf { it.isValid(context) }
            ?.let { return it.type }

    val labels = getLabels(context.projectConfig.typeLabelGroup)
    if (labels.size > 1) {
        context.w("Multiple type labels found on issue #${number}")
        return null
    }
    return labels.firstOrNull()?.name
        ?.removePrefix(context.projectConfig.typeLabelGroup.prefix)
}

fun GHIssue.setType(type: String, config: ProjectConfig) {
    val label = config.typeLabelGroup[type]
    if (label == null) {
        println("Invalid type: $type")
        return
    }
    setLabels(listOf(label), config.typeLabelGroup)
}

fun GHIssue.getComponents(context: RunContext): List<String> {
    val labels = if (this is GHPullRequest) {
        val fileSystem = FileSystems.getDefault()
        context.projectConfig.components
            .filter { component ->
                val matchers = component.paths
                    .map { fileSystem.getPathMatcher("glob:$it") }
                listFiles().any { file ->
                    matchers.any { it.matches(Paths.get(file.filename)) }
                }
            }
            .map { it.name }

    } else getLabels(context.projectConfig.componentsLabelGroup)
        .map { it.name }
    return labels.map { it.removePrefix(context.projectConfig.labels.components.prefix) }
}

fun GHIssue.setComponents(context: RunContext, components: List<String>) {
    val labels = components.map { context.projectConfig.componentsLabelGroup[it] }
        .also { labels ->
            labels.forEachIndexed { index, label ->
                if (label == null) println("Invalid component: ${components[index]}")
            }
        }
        .filterNotNull()
    setLabels(labels, context.projectConfig.componentsLabelGroup)
}

fun GHIssue.getPriority(context: RunContext): Int? {
    val labels = getLabels(context.projectConfig.priorityLabelGroup)
    if (labels.size > 1) {
        println("Multiple priority labels found on issue #${number}")
        return null
    }
    return labels.firstOrNull()
        ?.let { context.projectConfig.priorityLabelGroup.instances.indexOf(it) }
}

fun GHIssue.setPriority(context: RunContext, priority: Int) {
    setLabels(
        listOf(context.projectConfig.priorityLabelGroup.instances[priority]),
        context.projectConfig.priorityLabelGroup
    )
}

data class Label(
    val name: String,
    val color: String,
    val description: String?
) {
    fun get(repo: GHRepository): GHLabel {
        return try {
            repo.getLabel(name.encodedLabelName).also {
                if (it.color != color)
                    it.color = color
                if (it.description != description)
                    it.description = description
            }
        } catch (e: IOException) {
            println(e)
            repo.createLabel(name, color, description)
        }
    }
}

val String.encodedLabelName: String
    get() = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

class LabelGroup(
    val color: String,
    val prefix: String,
    val descriptionPrefix: String = "",
    instances: List<Pair<String, String?>>
) {
    val instances = instances.map { (title, description) ->
        Label(
            prefix + title,
            color,
            descriptionPrefix + (description ?: title)
        )
    }

    operator fun get(name: String) =
        instances.firstOrNull { it.name == name } ?: instances.firstOrNull { it.name == prefix + name }

    operator fun contains(label: Label) = label in instances
    operator fun contains(label: GHLabel) = label.name.startsWith(prefix)
}
// endregion

// region Release
class GHReleaseBuilder {

}

fun GHRepository.createRelease(version: SemVer, tagName: String = "v$version") {

}
// endregion
