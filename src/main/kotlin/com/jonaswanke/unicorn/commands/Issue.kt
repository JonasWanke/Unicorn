package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*

internal fun Unicorn.registerIssueCommands() {
    command("issue", "i") {
        help = "Groups commands like assigning and completing closed issues"

        command("assign", "a") {
            help = "Assigns the specified issue and creates the corresponding branch"

            run(
                argument("id", "ID of the GitHub issue")
                    .convert { if (it.startsWith("#")) it.substring(1) else it }
                    .int(),
                option(
                    "-a", "--assignee",
                    help = "GitHub usernames of people that are working on this issue with you (added as assignees to the issue)"
                )
                    .multiple(),
                option(
                    "-i", "--allow-issue-base",
                    help = "Allow branching the new branch off of an issue branch"
                )
                    .flag()
            ) { id, assignees, allowIssueBase ->
                val issue = gitHubRepo.getIssue(id)

                val assigneeAccounts = assignees.map {
                    gitHub.api.getUserOrNull(it)
                        ?: exit("User $it doesn't exist")
                }

                issue.assignTo(this, listOf(gitHub.api.myself) + assigneeAccounts, throwIfAlreadyAssigned = true)

                git.flow.createIssueBranch(
                    this, issue,
                    allowIssueBase = if (allowIssueBase) true else null
                )
            }
        }

        command("complete", "c") {
            help = "Opens a pull request for the currently active issue"

            run(
                argument("title", "PR title – short summary of your changes")
            ) { title ->
                val branch = git.flow.currentBranch(this) as? Git.Flow.IssueBranch
                    ?: exit("Current branch \"${git.currentBranchName}\" is not an issue branch")

                git.push(this)

                val issue = branch.issue
                issue.openPullRequest(this, title = title)
            }
        }
    }
}
