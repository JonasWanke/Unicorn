package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.convert
import com.jonaswanke.unicorn.script.parameters.int

internal fun Unicorn.registerIssueCommands() {
    command("issue", "i") {
        help = "Groups commands like assigning and completing closed issues"

        command("assign", "a") {
            help = "Assigns the specified issue and creates the corresponding branch"

            run(
                argument("id", "ID of the GitHub issue")
                    .convert { if (it.startsWith("#")) it.substring(1) else it }
                    .int()
            ) { id ->
                val issue = gitHubRepo.getIssue(id)
                issue.assignTo(this, gitHub.api.myself, throwIfAlreadyAssigned = true)

                git.flow.createIssueBranch(this, issue)
            }
        }

        command("complete", "c") {
            help = "Opens a pull request for the currently active issue"

            run(
                argument("description", "Short description of the PR (for Conventional Commit-style title)")
            ) { description ->
                val branch = git.flow.currentBranch(this, gitHub) as? Git.Flow.IssueBranch
                    ?: throw IllegalStateException("Current branch is not an issue branch")

                val issue = branch.issue
                val title = ConventionalCommit.format(this, issue, description)
                    ?: this.exit("Commit doesn't have a type label")
                issue.openPullRequest(this, title = title)
            }
        }
    }
}
