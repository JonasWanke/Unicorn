package com.jonaswanke.unicorn.commands.issue

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.jonaswanke.unicorn.BRANCH_ISSUE_PREFIX
import com.jonaswanke.unicorn.LABEL_COMPONENT_PREFIX
import com.jonaswanke.unicorn.LABEL_TYPE_PREFIX
import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.utils.GithubIssueUtils
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class CompleteIssue : BaseCommand(name = "complete") {
    private val title by argument("title")
        .convert {
            if (it.trim().length < 10) throw BadParameterValue("Must be at least 10 characters long", this)
            it
        }

    override fun run() {
        val repo = requireGithubRepo()
        val git = git

        val branch = git.repository.branch
        if (!branch.startsWith(BRANCH_ISSUE_PREFIX)) throw UsageError("Current branch is not an issue branch")
        val rawId = branch.removePrefix(BRANCH_ISSUE_PREFIX).substringBefore('-')
        val id = GithubIssueUtils.idFromString(rawId)
        val issue = GithubIssueUtils.issueFromId(repo, id)

        // Require labels
        val labels = GithubIssueUtils.checkLabels(issue)
        val type = labels.first { it.name.startsWith(LABEL_TYPE_PREFIX) }
            .name.removePrefix(LABEL_TYPE_PREFIX)
        val component = labels.firstOrNull { it.name.startsWith(LABEL_COMPONENT_PREFIX) }
            ?.name?.removePrefix(LABEL_COMPONENT_PREFIX)

        // Open prefilled compare-webpage to create a PR
        fun String.encode() = URLEncoder.encode(this, "UTF-8")

        var link = "${repo.htmlUrl}/compare/${branch.encode()}" +
                "?expand=1" +
                "&title=${(if (component != null) "$type($component): $title" else "$type: $title").encode()}" +
                "&${labels.joinToString("&") { "labels=${it.name.encode()}" }}" +
                "&assignee=${github.myself.login.encode()}"
        val milestone = issue.milestone
        if (milestone != null)
            link += "&milestone=${milestone.title.encode()}"
        Desktop.getDesktop().browse(URI(link))
    }
}
