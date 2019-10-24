package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.UsageError
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.utils.OAuthCredentialsProvider
import com.jonaswanke.unicorn.utils.echo
import com.jonaswanke.unicorn.utils.prompt
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.*
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.file.FileSystems
import java.nio.file.Paths
import org.kohsuke.github.GitHub as ApiGitHub

class GitHub(val api: ApiGitHub, val credentialsProvider: CredentialsProvider) {
    companion object {
        fun authenticateOrNull(config: GlobalConfig.GithubConfig? = Unicorn.globalConfig.github): GitHub? {
            config ?: return null

            val api = GitHubBuilder().apply {
                withOAuthToken(config.oauthToken, config.username)
                config.endpoint?.let { withEndpoint(it) }
            }.build()

            return api.takeIf { it.isCredentialValid }
                ?.let { GitHub(api, OAuthCredentialsProvider(config.oauthToken)) }
        }

        fun authenticateInteractive(
            forceNew: Boolean = false,
            username: String? = null,
            token: String? = null,
            endpoint: String? = null
        ): GitHub {
            if (!forceNew)
                authenticateOrNull()?.also { return it }

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
                Unicorn.globalConfig = Unicorn.globalConfig.copy(github = githubConfig)

                authenticateOrNull(githubConfig)?.let {
                    echo("Login successful")
                    return it
                }
                echo("Your credentials are invalid. Please try again.")
            }
        }

        fun authenticateWithToken(token: String): GitHub {
            val api = ApiGitHub.connect("anonymous", token)
            return GitHub(api, OAuthCredentialsProvider(token))
        }
    }

    fun currentRepoNameIfExists(directory: File = Unicorn.prefix): String? {
        return Git(directory).remoteList().mapNotNull { remoteConfig ->
            remoteConfig.urIs.firstOrNull { it.host == "github.com" }
        }
            .firstOrNull()
            ?.path
            ?.trimStart('/')
            ?.substringBeforeLast('.')
    }

    fun currentRepoIfExists(directory: File = Unicorn.prefix): GHRepository? {
        return currentRepoNameIfExists(directory)?.let { api.getRepository(it) }
    }

    fun currentRepo(directory: File = Unicorn.prefix): GHRepository {
        return currentRepoIfExists(directory)
            ?: throw UsageError("No repository is configured for the current project")
    }
}

// region Issue
fun GHIssue.assignTo(user: GHUser, throwIfAlreadyAssigned: Boolean = false) {
    val assignees = assignees
    if (assignees.size > 1 || (assignees.size == 1 && assignees.first() != user))
        throw UsageError("Issue $id is already assigned to ${assignees.joinToString { it.name }}")

    assignTo(user)
}


fun ConventionalCommit.Companion.format(issue: GHIssue, description: String, config: ProjectConfig): String? {
    val type = issue.getType(config) ?: return null
    return format(type, issue.getComponents(config), description)
}

fun GHIssue.openPullRequest(
    git: Git,
    title: String,
    assignees: List<String> = listOf(GitHub.authenticateInteractive().api.myself.login),
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
        .let { removeLabels(it) }

    // Add new labels
    labels.map { it.get(repository) }
        .filter { new -> this.labels.none { it.name == new.name } }
        .let { addLabels(it) }
}

fun GHIssue.getLabels(group: LabelGroup): List<Label> =
    labels.filter { it.name.startsWith(group.prefix) }
        .mapNotNull {
            group[it.name] ?: {
                println("Unknown label \"${it.name}\" with known prefix \"${group.prefix}\"")
                null
            }()
        }

fun GHIssue.getType(config: ProjectConfig): String? {
    if (this is GHPullRequest)
        ConventionalCommit.tryParse(title, config)
            ?.let { return it.type }

    val labels = getLabels(config.typeLabelGroup)
    if (labels.size > 1) {
        println("Multiple type labels found on issue #${number}")
        return null
    }
    return labels.firstOrNull()?.name
        ?.removePrefix(config.typeLabelGroup.prefix)
}

fun GHIssue.setType(type: String, config: ProjectConfig) {
    val label = config.typeLabelGroup[type]
    if (label == null) {
        println("Invalid type: $type")
        return
    }
    setLabels(listOf(label), config.typeLabelGroup)
}

fun GHIssue.getComponents(config: ProjectConfig): List<String> {
    val labels = if (this is GHPullRequest) {
        val fileSystem = FileSystems.getDefault()
        config.components
            .filter { component ->
                val matchers = component.paths
                    .map { fileSystem.getPathMatcher("glob:$it") }
                listFiles().any { file ->
                    matchers.any { it.matches(Paths.get(file.filename)) }
                }
            }
            .map { it.name }

    } else getLabels(config.componentsLabelGroup)
        .map { it.name }
    return labels.map { it.removePrefix(config.labels.components.prefix) }
}

fun GHIssue.setComponents(components: List<String>, config: ProjectConfig) {
    val labels = components.map { config.componentsLabelGroup[it] }
        .also { labels ->
            labels.forEachIndexed { index, label ->
                if (label == null) println("Invalid component: ${components[index]}")
            }
        }
        .filterNotNull()
    setLabels(labels, config.componentsLabelGroup)
}

fun GHIssue.getPriority(config: ProjectConfig): Int? {
    val labels = getLabels(config.priorityLabelGroup)
    if (labels.size > 1) {
        println("Multiple priority labels found on issue #${number}")
        return null
    }
    return labels.firstOrNull()
        ?.let { config.priorityLabelGroup.instances.indexOf(it) }
}

fun GHIssue.setPriority(priority: Int, config: ProjectConfig) {
    setLabels(listOf(config.priorityLabelGroup.instances[priority]), config.priorityLabelGroup)
}

data class Label(
    val name: String,
    val color: String,
    val description: String?
) {
    fun get(repo: GHRepository): GHLabel {
        return try {
            repo.getLabel(URLEncoder.encode(name, "UTF-8").replace("+", "%20")).also {
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

class LabelGroup(
    val color: String,
    val prefix: String,
    val descriptionPrefix: String = "",
    instances: List<Pair<String, String?>>
) {
    val instances =
        instances.map { (title, description) -> Label(prefix + title, color, descriptionPrefix + description) }

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
