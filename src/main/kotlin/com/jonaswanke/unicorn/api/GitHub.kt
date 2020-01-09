@file:Suppress("unused")

package com.jonaswanke.unicorn.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.ajalt.clikt.core.UsageError
import com.jonaswanke.unicorn.core.*
import com.jonaswanke.unicorn.core.ProjectConfig.CategorizationConfig.*
import com.jonaswanke.unicorn.utils.*
import kotlinx.serialization.Serializable
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.*
import java.awt.Desktop
import java.net.URI
import java.net.URL
import java.net.URLEncoder
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

        private class OAuthCredentialsProvider(token: String) : UsernamePasswordCredentialsProvider(token, "")

        fun authenticate(
            context: RunContext,
            forceNew: Boolean = false,
            username: String? = null,
            token: String? = null,
            endpoint: String? = null
        ): GitHub = context.group("GitHub login") {
            if (!forceNew)
                authenticateOrNull(this)?.also { return@group it }
            if (this !is InteractiveRunContext) {
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

            log.i("Please enter your GitHub credentials (They will be stored unencrypted in the installation directory):")
            while (true) {
                val usernameAct = username
                    ?: prompt("GitHub username")
                val tokenAct = token
                    ?: prompt("Personal access token (with repo scope)", hideInput = true)
                val endpointAct = endpoint
                    ?: promptOptional("Custom GitHub endpoint?")

                val githubConfig = GlobalConfig.GitHubConfig(
                    username = usernameAct, oauthToken = tokenAct, endpoint = endpointAct
                )
                globalConfig = globalConfig.copy(gitHub = githubConfig)

                authenticateOrNull(context)?.let {
                    log.i("Login successful")
                    return@group it
                }
                log.w("Your credentials are invalid. Please try again.")
            }

            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Can't be reached")
        }
    }

    fun currentRepoNameOrNull(context: RunContext): String? {
        if (!Git.isInitializedIn(context.projectDir)) return null

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

// region User
fun ApiGitHub.getUserOrNull(login: String): GHUser? {
    return try {
        getUser(login)
    } catch (e: GHFileNotFoundException) {
        null
    }
}
// endregion

// region Repository
fun ApiGitHub.createRepository(config: GHRepositoryCreationConfig): GHRepository =
    createRepository(config.name).create(config)

fun GHOrganization.createRepository(config: GHRepositoryCreationConfig): GHRepository =
    createRepository(config.name).create(config)

private fun GHCreateRepositoryBuilder.create(config: GHRepositoryCreationConfig): GHRepository {
    config.applyTo(this)
    return try {
        create()
    } catch (e: HttpException) {
        val error = e.message?.let { GHApiError.parse(it) } ?: throw e
        when {
            error.errors.any { it.field == "name" && it.message == "name already exists on this account" } ->
                throw GHRepoWithNameAlreadyExistsException(config.name)
            error.message == "Visibility can't be private. Please upgrade your subscription to create a new private repository." ->
                throw GHRepoCantBePrivateException()
            else -> throw e
        }
    }
}

class GHRepoWithNameAlreadyExistsException(repositoryName: String) :
    Exception("A repository called \"$repositoryName\" already exists on this account")

class GHRepoCantBePrivateException :
    Exception("Visibility can't be private. Please upgrade your subscription to create a new private repository.")

data class GHRepositoryCreationConfig(
    val name: String,
    val description: String? = null,
    val homepage: URL? = null,
    val private: Boolean = false,
    val issues: Boolean = true,
    val wiki: Boolean = true,
    val autoInit: Boolean = false,
    val gitignoreTemplate: String? = null,
    val licenseTemplate: String? = null,
    val allowMergeCommit: Boolean = true,
    val allowSquashMerge: Boolean = true,
    val allowRebaseMerge: Boolean = true
) {
    internal fun applyTo(builder: GHCreateRepositoryBuilder) {
        builder.also {
            it.description(description)
            it.homepage(homepage)
            it.private_(private)
            it.issues(issues)
            it.wiki(wiki)
            it.autoInit(autoInit)
            it.gitignoreTemplate(gitignoreTemplate)
            it.licenseTemplate(licenseTemplate)
            it.allowMergeCommit(allowMergeCommit)
            it.allowSquashMerge(allowSquashMerge)
            it.allowRebaseMerge(allowRebaseMerge)
        }
    }
}
// endregion

// region Issue
fun String.toIssueId(context: RunContext): Int {
    val trimmed = if (startsWith("#")) substring(1) else this
    return trimmed.toIntOrNull()
        ?: context.exit("Couldn't parse issue id \"$trimmed\"")
}

fun GHIssue.assignTo(context: RunContext, users: List<GHUser>, throwIfAlreadyAssigned: Boolean = false): List<GHUser> =
    context.group("Assign GitHub issue #$number to ${users.joinToString { it.login }}") {
        val oldAssignees = assignees
        log.d("Current assignees: ${oldAssignees.joinToString { it.login }}")

        if (throwIfAlreadyAssigned && oldAssignees.isNotEmpty() && oldAssignees.toSet() != users.toSet())
            exit("Issue is already assigned")

        setAssignees(users)
        log.i("Successfully assigned")

        oldAssignees
    }


fun ConventionalCommit.Companion.format(
    context: RunContext,
    issue: GHIssue,
    description: String
): String? {
    val type = issue.getType(context) ?: return null
    return format(type.name, issue.getComponents(context).map { it.name }, description)
}

fun GHIssue.openPullRequest(
    context: RunContext,
    title: String,
    git: Git = context.git,
    assignees: List<String> = listOf(GitHub.authenticate(context).api.myself.login),
    labels: List<String> = getLabels().map { it.name },
    milestone: String? = this.milestone?.title,
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
    get() {
        return PR_ISSUES_REGEX.findAll(body)
            .flatMap { result ->
                result.groups[1]!!.value.splitToSequence(',')
            }
            .mapNotNull { issueIdString ->
                issueIdString.takeUnless { it.isBlank() }
                    ?.trim()
                    ?.removePrefix("#")
                    ?.toIntOrNull()
            }
            .toSortedSet()
            .map { repository.getIssue(it) }
    }

val GHPullRequest.isBreaking: Boolean
    get() = labels.any { it.name.contains("breaking", ignoreCase = true) }

fun Glob.matches(file: GHPullRequestFileDetail): Boolean = matches(file.filename)

fun GHPullRequest.toCommitMessage() = buildString {
    val commit = ConventionalCommit.parse(title)
    append(commit.description)

    val issues = closedIssues
    if (issues.isNotEmpty())
        append(closedIssues.joinToString(prefix = ", fixes ") { "[#${it.number}](${it.htmlUrl})" })
}
// endregion

// region Label
private val GHIssue.issuePrNumber: String
    get() = "${if (this is GHPullRequest) "PR" else "issue"} #$number"

fun <V : CategorizationValue> GHIssue.setLabels(
    context: RunContext,
    values: List<Categorization.ResolvedValue<V>>,
    categorization: Categorization<V>
) = context.group("Setting ${categorization.name}-label(s) of $issuePrNumber to ${values.joinToString { it.name }}") {
    val upToDateInstance = if (this is GHPullRequest) repository.getPullRequest(number) else repository.getIssue(number)
    val labelsToKeep = upToDateInstance.labels
        .filter { !it.name.startsWith(categorization.labels.prefix) }
        .map { it.name }
    log.d {
        +"Keeping labels "
        joined(labelsToKeep) { kbd(it) }
    }

    val additionalLabels = values.map { it.fullName }
    log.d {
        +"Adding labels "
        joined(additionalLabels) { kbd(it) }
    }

    val labels = (labelsToKeep + additionalLabels).toTypedArray()
    setLabels(*labels)

    if (this is GHPullRequest) refresh()
}

fun <V : CategorizationValue> GHIssue.getLabels(
    context: RunContext,
    category: Categorization<V>
): List<Categorization.ResolvedValue<V>> {
    return labels.filter { it.name.startsWith(category.labels.prefix) }
        .mapNotNull {
            category.getOrNull(it.name) ?: {
                context.log.w {
                    +"Unknown label "
                    kbd(it.name)
                    +" with known prefix \"${category.labels.prefix}\""
                }
                null
            }()
        }
}

fun GHRepository.createLabelIfNotExists(name: String, color: String, description: String? = null): GHLabel? {
    return try {
        createLabel(name, color, description ?: "")
    } catch (e: HttpException) {
        val error = e.message?.let { GHApiError.parse(it) } ?: throw e
        if (error.errors.any { it.resource == "Label" && it.code == "already_exists" }) return null
        throw e
    }
}

// region Categorization
fun <V : CategorizationValue> Categorization.ResolvedValue<V>.getGhLabel(repo: GHRepository): GHLabel {
    return getGhLabelOrNull(repo) ?: repo.createLabel(fullName, color, fullDescription)
}

fun <V : CategorizationValue> Categorization.ResolvedValue<V>.getGhLabelOrNull(repo: GHRepository): GHLabel? {
    return try {
        repo.getLabel(fullName).also {
            if (it.color != color)
                it.color = color
            if (it.description != fullDescription)
                it.description = fullDescription
        }
    } catch (e: GHFileNotFoundException) {
        null
    }
}

private const val LABEL_DEPRECATED_PREFIX = "[deprecated] "
fun <V : CategorizationValue> Categorization.ResolvedValue<V>.deprecate(repo: GHRepository) {
    val label = getGhLabelOrNull(repo) ?: return

    val description = when {
        fullDescription.startsWith(LABEL_DEPRECATED_PREFIX) -> fullDescription
        else -> LABEL_DEPRECATED_PREFIX + fullDescription.removePrefix(LABEL_DEPRECATED_PREFIX, ignoreCase = true)
    }
    label.description = description
}
// endregion

// region Component
fun GHIssue.getComponents(context: RunContext): List<Categorization.ResolvedValue<ComponentConfig.Component>> {
    return if (this is GHPullRequest) {
        context.projectConfig.categorization.component.resolvedValues
            .associateWith { component -> component.value.paths.map { Glob(it) } }
            .filter { (_, matchers) ->
                listFiles().any { file ->
                    matchers.any { it.matches(file) }
                }
            }
            .map { (component, _) -> component }
    } else getLabels(context, context.projectConfig.categorization.component)
}

fun GHIssue.setComponents(
    context: RunContext,
    components: List<Categorization.ResolvedValue<ComponentConfig.Component>>
) {
    setLabels(context, components, context.projectConfig.categorization.component)
}

fun GHRepository.syncComponentLabels(context: RunContext) = context.group("Syncing component labels") {
    projectConfig.categorization.component.resolvedValues.forEach {
        log.i {
            +"Syncing label "
            kbd(it.fullName)
        }
        it.getGhLabel(this@syncComponentLabels)
    }
}
// endregion

// region Priority
fun GHIssue.getPriority(context: RunContext): Int? {
    val labels = getLabels(context, context.projectConfig.categorization.priority)
    if (labels.size > 1) {
        context.log.w("Multiple priority labels found on $issuePrNumber #${number}")
        return null
    }
    return labels.firstOrNull()
        ?.let { context.projectConfig.categorization.priority.resolvedValues.indexOf(it) }
}

fun GHIssue.setPriority(context: RunContext, priority: Int) {
    val priorities = context.projectConfig.categorization.priority
    if (priority !in priorities.resolvedValues.indices) {
        context.log.w("Invalid priority index: $priority")
        return
    }
    setLabels(context, listOf(priorities.resolvedValues[priority]), priorities)
}

fun GHRepository.syncPriorityLabels(context: RunContext) = context.group("Syncing priority labels") {
    projectConfig.categorization.priority.resolvedValues.forEach {
        log.i {
            +"Syncing label "
            kbd(it.fullName)
        }
        it.getGhLabel(this@syncPriorityLabels)
    }
}
// endregion

// region Type
fun GHIssue.getType(context: RunContext): Categorization.ResolvedValue<TypeConfig.Type>? {
    if (this is GHPullRequest)
        ConventionalCommit.tryParse(title)
            ?.takeIf { it.isValid(context) }
            ?.let { return it.resolveType(context) }

    val labels = getLabels(context, context.projectConfig.categorization.type)
    if (labels.size > 1) {
        context.log.w("Multiple type labels found on $issuePrNumber")
        return null
    }
    return labels.firstOrNull()?.name?.let {
        context.projectConfig.categorization.type.getOrNull(it)
            ?: {
                context.log.w("Unknown type $it on $issuePrNumber")
                null
            }()
    }
}

fun GHIssue.setType(context: RunContext, type: Categorization.ResolvedValue<TypeConfig.Type>) {
    setLabels(context, listOf(type), context.projectConfig.categorization.type)
}

fun GHRepository.syncTypeLabels(context: RunContext) = context.group("Syncing type labels") {
    projectConfig.categorization.type.resolvedValues.forEach {
        log.i {
            +"Syncing label "
            kbd(it.fullName)
        }
        it.getGhLabel(this@syncTypeLabels)
    }
}
// endregion
// endregion

// region Release
val GHRepository.latestReleaseInclPrerelease: GHRelease?
    get() = listReleases().firstOrNull()

private val COMMIT_MESSAGE_MERGE_PR = "Merge pull request #(?<id>[0-9]+).*".toRegex(RegexOption.IGNORE_CASE)

fun GHRepository.getMergedPrsSinceLastRelease(context: RunContext): List<GHPullRequest> {
    val latestRelease = latestReleaseInclPrerelease
    val commits =
        if (latestRelease == null) context.git.allCommits(context)
        else context.git.commitsSinceTag(context, latestRelease.tagName)

    return commits.asSequence()
        .map { it.shortMessage }
        .mapNotNull { COMMIT_MESSAGE_MERGE_PR.matchEntire(it)?.groups?.get("id")?.value }
        .map { it.toInt() }
        .map { getPullRequest(it) }
        .toList()
}

val GHRepository.latestReleaseVersion: SemVer?
    get() = latestRelease?.tagName?.removePrefix("v")?.let { SemVer.parseOrNull(it) }
// endregion

// region API errors
@Serializable
data class GHApiError(
    val message: String = "",
    val errors: List<Error> = emptyList(),
    @JsonProperty("documentation_url") val documentationUrl: String? = null
) {
    companion object {
        fun parse(rawJson: String): GHApiError = json.parse(serializer(), rawJson)
    }

    @Serializable
    data class Error(
        val resource: String = "",
        val field: String = "",
        val code: String = "",
        val message: String? = null
    )
}
// endregion
