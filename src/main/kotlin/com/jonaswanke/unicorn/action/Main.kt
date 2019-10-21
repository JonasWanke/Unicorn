package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.script.ConventionalCommit
import com.jonaswanke.unicorn.script.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.closedIssues
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import kotlin.system.exitProcess


fun main() {
    Unicorn.prefix = Action.Env.githubWorkspace
    val repoToken = Action.getRequiredInput("repo-token")

    val gh = GitHub.authenticateWithToken(repoToken)
    val repo = gh.api.getRepository(Action.Env.githubRepository)
    val projectConfig = Unicorn.getProjectConfig()

    val payload = Action.getPayload()
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
