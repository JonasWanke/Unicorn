package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.core.RunContext
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import kotlin.system.exitProcess


fun main() {
    val context = GitHubActionRunContext()

    val gh = GitHub.authenticate(context)
    val repo = gh.api.getRepository(Action.Env.githubRepository)

    val payload = Action.getPayload()
    payload.pullRequest ?: throwError("Unicorn currently only supports events of type pull_request")

    val pr = repo.getPullRequest(payload.pullRequest.number)

    assignAuthors(pr)
    inferLabels(context, pr)

    val reportCollector = ReportLogCollector()
    runChecks(context, reportCollector, pr)
    val report = Report(reportCollector.reportItems)
    pr.createOrUpdateComment("unicorn-report", report.toString())

    // Notify GitHub of erroneous status
    if (report.severity == Report.Severity.ERROR) exitProcess(1)
}

private fun assignAuthors(pr: GHPullRequest) {
    pr.listCommits().map { it.commit.author }
        .let { pr.addAssignees() }
}

private fun inferLabels(context: RunContext, pr: GHPullRequest) {
    val closedIssues = pr.closedIssues

    pr.getType(context)
        ?.let { pr.setType(context, it) }

    pr.getComponents(context)
        .let { pr.setComponents(context, it) }

    closedIssues.mapNotNull { it.getPriority(context) }.max()
        ?.let { pr.setPriority(context, it) }
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
