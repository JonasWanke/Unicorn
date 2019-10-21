package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.UsageError
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.utils.OAuthCredentialsProvider
import com.jonaswanke.unicorn.utils.echo
import com.jonaswanke.unicorn.utils.prompt
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
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


const val LABEL_TYPE_PREFIX = "T: "
val GHIssue.type: String
    get() {
        if (labels.count { it.name.startsWith(LABEL_TYPE_PREFIX) } != 1)
            throw UsageError("Issue $id ($title) must have exactly one label specifying its type: <$LABEL_TYPE_PREFIX[feat,fix,...]>")

        return labels.first { it.name.startsWith(LABEL_TYPE_PREFIX) }
            .name.removePrefix(LABEL_TYPE_PREFIX)
    }

const val LABEL_COMPONENT_PREFIX = "C: "
val GHIssue.components
    get() = labels.filter { it.name.startsWith(LABEL_COMPONENT_PREFIX) }
        .map { it.name.removePrefix(LABEL_COMPONENT_PREFIX) }


fun ConventionalCommit.Companion.format(issue: GHIssue, description: String): String {
    return format(issue.type, issue.components, description)
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

// region Release
class GHReleaseBuilder {

}

fun GHRepository.createRelease(version: SemVer, tagName: String = "v$version") {

}
// endregion
