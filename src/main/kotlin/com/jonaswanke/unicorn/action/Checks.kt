package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.script.ConventionalCommit
import com.jonaswanke.unicorn.script.closedIssues
import org.kohsuke.github.GHPullRequest


internal fun runChecks(pr: GHPullRequest, config: ProjectConfig): List<CheckResult> = listOfNotNull(
    runTitleCheck(pr, config),
    runClosedIssuesCheck(pr),
    runCommitCheck(pr, config)
)

private fun runTitleCheck(pr: GHPullRequest, config: ProjectConfig): CheckResult {
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
                    val allowedTypes = config.types.list.joinToString { "<kbd>${it.name}</kbd>" }
                    CheckResult.error("type <kbd>${error.type}</kbd> is invalid", "allowed values are: $allowedTypes")
                }
                is ConventionalCommit.ValidationError.InvalidScopes -> {
                    val allowedComponents = config.components.joinToString { "<kbd>${it.name}</kbd>" }
                    val invalidComponents =
                        error.scopes.joinToString { "<kbd>${it.value}</kbd> (position ${it.index + 1})" }
                    val help = "allowed values are: $allowedComponents"
                    if (error.scopes.size == 1)
                        CheckResult.error("component $invalidComponents is invalid", help)
                    else
                        CheckResult.error("components $invalidComponents are invalid", help)
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

private fun runCommitCheck(pr: GHPullRequest, config: ProjectConfig): CheckResult? {
    return pr.listCommits().filter { ConventionalCommit.tryParse(it.commit.message, config) == null }
        .map { CheckResult.warning("`${it.commit.message}`") }
        .let {
            if (it.isEmpty()) return null

            val title =
                "The following commit ${if (it.size == 1) "message doesn't" else "messages don't"}  follow <a href=\"https://www.conventionalcommits.org/en/v1.0.0\">conventional commits</a>"
            CheckResult.Group(title, it)
        }
}
