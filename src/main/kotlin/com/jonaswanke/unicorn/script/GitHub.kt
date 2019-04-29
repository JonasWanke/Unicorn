package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.output.TermUi.prompt
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.utils.OAuthCredentialsProvider
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.transport.CredentialsProvider
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHUser
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import org.kohsuke.github.GitHub as ApiGitHub

class GitHub(val api: ApiGitHub, val credentialsProvider: CredentialsProvider) {
    companion object {
        fun getIfAuthenticated(): GitHub? {
            Unicorn.globalConfig.github?.also { githubConfig ->
                val api = githubConfig.buildGitHubApi()
                if (api.isCredentialValid)
                    return GitHub(api, OAuthCredentialsProvider(githubConfig.oauthToken))
                echo("The stored GitHub credentials are invalid")
            }
            return null
        }

        fun authenticate(
            forceNew: Boolean = false,
            username: String? = null,
            token: String? = null,
            endpoint: String? = null
        ): GitHub {
            if (!forceNew)
                getIfAuthenticated()?.also { return it }

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
                val github = githubConfig.buildGitHubApi()

                val isValid = github.isCredentialValid
                if (isValid) {
                    echo("Login successful")
                    return GitHub(github, OAuthCredentialsProvider(githubConfig.oauthToken))
                } else
                    echo("Your credentials are invalid. Please try again.")
            }
        }
    }

    val currentRepoName: String?
        get() {
            return Git.remoteList().mapNotNull { remoteConfig ->
                remoteConfig.urIs.firstOrNull { it.host == "github.com" }
            }
                .firstOrNull()
                ?.path
                ?.trimStart('/')
                ?.substringBeforeLast('.')
        }
    val currentRepo: GHRepository?
        get() = currentRepoName?.let { api.getRepository(it) }
    val requireCurrentRepo: GHRepository
        get() = currentRepo
            ?: throw UsageError("No repository is configured for the current project")
}

// region Issue
fun GHIssue.assignTo(user: GHUser, throwIfAlreadyAssigned: Boolean = false) {
    val assignees = assignees
    if (assignees.size > 1 || (assignees.size == 1 && assignees.first() != user))
        throw UsageError("Issue $id is already assigned to ${assignees.joinToString { it.name }}")

    assignTo(user)
}


const val LABEL_TYPE_PREFIX = "T: "
val GHIssue.type: ConventionalCommits.Type
    get() {
        if (labels.count { it.name.startsWith(LABEL_TYPE_PREFIX) } != 1)
            throw UsageError("Issue $id ($title) must have exactly one label specifying its type: <$LABEL_TYPE_PREFIX[feat,fix,...]>")

        return labels.first { it.name.startsWith(LABEL_TYPE_PREFIX) }
            .name.removePrefix(LABEL_TYPE_PREFIX)
            .let { ConventionalCommits.Type.fromString(it) }
    }

const val LABEL_COMPONENT_PREFIX = "C: "
val GHIssue.components
    get() = labels.filter { it.name.startsWith(LABEL_COMPONENT_PREFIX) }
        .map { it.name.removePrefix(LABEL_COMPONENT_PREFIX) }


fun ConventionalCommits.format(issue: GHIssue, description: String): String {
    return format(issue.type, issue.components, description)
}

fun GHIssue.openPullRequest(
    title: String,
    assignees: List<String> = listOf(GitHub.authenticate().api.myself.login),
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
        append(Git.currentBranchName.encode())
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
