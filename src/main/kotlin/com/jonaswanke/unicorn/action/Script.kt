package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.action.Action.throwError
import com.jonaswanke.unicorn.api.github.createOrUpdateComment
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.UnicornMarker


typealias GitHubActionBody = GitHubActionRunContext.() -> Unit

object GitHubAction {
    var body: GitHubActionBody? = null
        internal set

    var isRunSuccessful = true
}

@UnicornMarker
fun Unicorn.gitHubAction(body: GitHubActionBody) {
    GitHubAction.body = body
}


typealias ChecksBody = CheckContext.() -> Unit

@UnicornMarker
fun GitHubActionRunContext.runChecks(identifier: String = "unicorn-report", body: ChecksBody) {
    if (event !is Action.Event.PullRequest) {
        throwError("Running checks is only supported on pull request events")
    }

    val pullRequest = event.pullRequest
    val context = CheckContext(this, ReportLogCollector(), pullRequest)
    context.body()
    val report = Report(context.reportCollector.reportItems)
    if (report.severity == Report.Severity.ERROR) GitHubAction.isRunSuccessful = false

    pullRequest.createOrUpdateComment(identifier, report.toString())
}
