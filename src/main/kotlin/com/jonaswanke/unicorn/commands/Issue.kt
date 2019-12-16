package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.script.*
import com.jonaswanke.unicorn.script.command.argument
import com.jonaswanke.unicorn.script.command.command
import com.jonaswanke.unicorn.script.command.executableCommand
import com.jonaswanke.unicorn.script.command.register

internal fun Unicorn.registerIssueCommands() {
    command(
        "issue",
        aliases = listOf("i"),
        help = "Groups commands like assigning and completing closed issues"
    ) {
        executableCommand(
            "assign",
            help = "Assigns the specified issue and creates the corresponding branch"
        ) {
            val id by argument("id", help = "ID of the GitHub issue").register()

            return@executableCommand {
                val issue = gitHubRepo.getIssue(id.toIssueId(this))
                issue.assignTo(this, gitHub.api.myself, throwIfAlreadyAssigned = true)

                git.flow.createIssueBranch(this, issue)
            }
        }

        executableCommand(
            "complete",
            help = "Opens a pull request for the currently active issue"
        ) {
            val description by argument(
                "description",
                help = "Short description of the PR (for Conventional Commit-style title)"
            ).register()

            return@executableCommand run@{
                val branch = git.flow.currentBranch(this, gitHub) as? Git.Flow.IssueBranch
                    ?: throw IllegalStateException("Current branch is not an issue branch")

                val issue = branch.issue
                val title = ConventionalCommit.format(this, issue, description)
                if (title == null) {
                    this.e("Commit doesn't have a type label")
                    return@run
                }
                issue.openPullRequest(this, title = title)
            }
        }
    }
}
