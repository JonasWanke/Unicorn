package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.script.*
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import java.nio.file.FileSystems
import java.nio.file.Paths
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

    // Type
    pr.getType(context)
        ?.let { pr.setType(it, context.projectConfig) }

    // Components
    val fileSystem = FileSystems.getDefault()
    context.projectConfig.components
        .associateWith { it.paths }
        .mapValues { (_, paths) ->
            paths.map { fileSystem.getPathMatcher("glob:$it") }
        }
        .filter { (_, matchers) ->
            pr.listFiles().any { file ->
                matchers.any { it.matches(Paths.get(file.filename)) }
            }
        }
        .map { (component, _) -> component.name }
        .let { pr.setComponents(context, it) }

    // Priority
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
