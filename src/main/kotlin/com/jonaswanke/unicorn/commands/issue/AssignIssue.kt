package com.jonaswanke.unicorn.commands.issue

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.jonaswanke.unicorn.BRANCH_DEV
import com.jonaswanke.unicorn.BRANCH_ISSUE_PREFIX
import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.utils.GithubIssueUtils
import com.jonaswanke.unicorn.utils.call
import com.jonaswanke.unicorn.utils.createBranch

class AssignIssue : BaseCommand(name = "assign") {
    val id by argument("id")
        .convert { GithubIssueUtils.idFromString(it) }

    override fun run() {
        val repo = requireGithubRepo()
        val issue = GithubIssueUtils.issueFromId(repo, id)

        // Require labels
        GithubIssueUtils.checkLabels(issue)

        // Assign
        issue.assignTo(github.myself)

        // Checkout dev-branch for the latest code
        val git = git
        call(git.checkout()) {
            setName(BRANCH_DEV)
        }
        call(git.pull())

        // Create branch
        val safeTitle = issue.title
            .replace(Regex("[^a-z0-9]", RegexOption.IGNORE_CASE), "-")
            .toLowerCase()
        createBranch(git, "$BRANCH_ISSUE_PREFIX$id-$safeTitle")
    }
}
