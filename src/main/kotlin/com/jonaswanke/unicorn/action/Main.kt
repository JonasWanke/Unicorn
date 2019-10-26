package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.script.*
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import java.nio.file.FileSystems
import java.nio.file.Paths
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
    inferLabels(pr, projectConfig)

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

private fun inferLabels(pr: GHPullRequest, config: ProjectConfig) {
    val closedIssues = pr.closedIssues

    // Type
    pr.getType(config)?.let { pr.setType(it, config) }

    // Components
    val fileSystem = FileSystems.getDefault()
    config.components
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
        .let { pr.setComponents(it, config) }

    // Priority
    closedIssues.mapNotNull { it.getPriority(config) }.max()
        ?.let { pr.setPriority(it, config) }
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
