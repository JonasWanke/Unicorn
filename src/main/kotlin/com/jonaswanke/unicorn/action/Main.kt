package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.script.*
import org.kohsuke.github.GHIssue
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

    assignAuthors(pr)

    val checkResults = runChecks(pr, projectConfig)
    val report = Report(checkResults = checkResults)
    pr.createOrUpdateComment("unicorn-report", report.toString())

    // Notify GitHub of erroneous status
    if (report.severity == Report.Severity.ERROR) exitProcess(1)
}

private fun assignAuthors(pr: GHPullRequest) {
    pr.listCommits().map { it.commit.author }
        .let { pr.addAssignees() }
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
