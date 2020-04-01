package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.core.RunContext
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import kotlin.system.exitProcess


fun main() {
    val context = GitHubActionRunContext()
    val repo = context.gitHub.api.getRepository(Action.Env.githubRepository)
    /*when (context.git.flow.currentBranch(context, repo)) {
        is Git.Flow.MasterBranch -> throwError("Unicorn action is not supported on the master branch")
        is Git.Flow.IssueBranch -> Unit
        is Git.Flow.ReleaseBranch -> {
            context.log.i("Running on a release branch. Exiting")
            return
        }
        is Git.Flow.HotfixBranch -> {
            context.log.w("Hotfixes are not yet supported by Unicorn")
            return
        }
    }*/

    val payload = Action.getPayload()
    payload.pullRequest ?: throwError("Unicorn currently only supports events of type pull_request")

    val pr = repo.getPullRequest(payload.pullRequest.number)

    assignAuthor(pr)
    inferLabels(context, pr)

    val reportCollector = ReportLogCollector()
    runChecks(context, reportCollector, pr)
    val report = Report(reportCollector.reportItems)
    pr.createOrUpdateComment("unicorn-report", report.toString())

    // Notify GitHub of erroneous status
    if (report.severity == Report.Severity.ERROR) exitProcess(1)
}

private fun assignAuthor(pr: GHPullRequest) {
    // It doesn't seem possible yet to get GitHub accounts of all commit authors, hence we only assign the person who
    // opened this PR for now.
    pr.addAssignees(pr.user)
}

private fun inferLabels(context: RunContext, pr: GHPullRequest) {
    val closedIssues = pr.closedIssues

    if (pr.getType(context) == null) {
        val type = closedIssues
            .mapNotNull { it.getType(context) }
            .toSet()
            .singleOrNull()
        if (type != null) pr.setType(context, type)
    }

    pr.inferComponents(context)
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
