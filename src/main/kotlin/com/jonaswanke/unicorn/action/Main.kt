package com.jonaswanke.unicorn.action

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.script.ConventionalCommit
import com.jonaswanke.unicorn.script.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.closedIssues
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import java.io.File


fun main() {
    Unicorn.prefix = System.getenv("GITHUB_WORKSPACE")
        ?.let { File(it) }
        ?: throwError("GITHUB_WORKSPACE not set")
    val repoToken = getRequiredInput("repo-token")

    val gh = GitHub.authenticateWithToken(repoToken)
    val repo = System.getenv("GITHUB_REPOSITORY")
        ?.let { gh.api.getRepository(it) }
        ?: throwError("GITHUB_REPOSITORY not set")
    val projectConfig = Unicorn.getProjectConfig()

    val eventFile = System.getenv("GITHUB_EVENT_PATH")?.let { File(it) }
        ?: throwError("GITHUB_EVENT_PATH not set")
    val payload = ObjectMapper(JsonFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .readValue(eventFile, WebhookPayload::class.java)
    payload.pullRequest ?: throwError("Unicorn currently only supports events of type pull_request")

    val pr = repo.getPullRequest(payload.pullRequest.number)

    val checkResults = runChecks(pr, projectConfig)
    val report = Report(checkResults = checkResults)
    pr.createOrUpdateComment("unicorn-report", report.toString())
}


private fun runChecks(pr: GHPullRequest, config: ProjectConfig): List<CheckResult> = listOf(
    runTitleChecks(pr, config),
    runClosedIssuesCheck(pr)
)

private fun runTitleChecks(pr: GHPullRequest, config: ProjectConfig): CheckResult {
    val results = mutableListOf<CheckResult>()
    val title = try {
        ConventionalCommit.parse(pr.title)
    } catch (e: IllegalArgumentException) {
        results += CheckResult.error("doesn't follow <a href=\"https://www.conventionalcommits.org/en/v1.0.0\">conventional commits</a>")
        null
    }
    if (title != null)
        results += title.validate(config).map { error ->
            when (error) {
                is ConventionalCommit.ValidationError.InvalidType -> {
                    val allowedTypes = config.types.list.joinToString { "`$it`" }
                    CheckResult.error("type `${error.type}` is invalid", "allowed values are: $allowedTypes")
                }
                is ConventionalCommit.ValidationError.InvalidScopes -> {
                    val allowedComponents = config.components.joinToString { "`$it`" }
                    val invalidComponents = error.scopes.joinToString { "`${it.value}` (position ${it.index + 1})" }
                    val help = "allowed values are: $allowedComponents"
                    if (error.scopes.size == 1)
                        CheckResult.error("component `$invalidComponents` is invalid", help)
                    else
                        CheckResult.error("components `$invalidComponents` are invalid", help)
                }
            }
        }
    return CheckResult.Group("PR title", results)
}

private fun runClosedIssuesCheck(pr: GHPullRequest): CheckResult {
    val closedIssues = pr.closedIssues
    if (closedIssues.isEmpty()) return CheckResult.warning(
        "This PR won't close any issues",
        "Reference issues in you PR description using <code>Closes: #issueId</code>"
    )
    val closedIssuesString = closedIssues.joinToString { "#${it.number}" }
    return CheckResult.info("This PR will close the following issues: $closedIssuesString")
}

private fun GHIssue.createOrUpdateComment(identifier: String, body: String) {
    val commentedIdentifier = "<!-- $identifier -->\n"
    val newBody = commentedIdentifier + body
    comments
        .firstOrNull { commentedIdentifier in it.body }
        ?.also {
            it.update(newBody)
            return
        }

    comment(newBody)
}

data class WebhookPayload(
    @JsonProperty("pull_request")
    val pullRequest: PullRequest? = null
) {
    data class PullRequest(
        @JsonProperty("number")
        val number: Int
    )
}
