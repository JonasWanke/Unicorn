package com.jonaswanke.aluminum.commands.feature

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.jonaswanke.aluminum.BRANCH_DEV
import com.jonaswanke.aluminum.commands.BaseCommand
import com.jonaswanke.aluminum.utils.call
import com.jonaswanke.aluminum.utils.trackBranch
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GHFileNotFoundException
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class AssignFeature : BaseCommand(name = "assign") {
    val id by argument("id").convert { rawId ->
        rawId.toIntOrNull()?.takeIf { it > 0 }
            ?: throw BadParameterValue("$rawId is not a valid issue ID")
    }

    override fun run() {
        val repo = githubRepo
            ?: throw UsageError("No repository is configured for the current project")
        val issue = try {
            repo.getIssue(id)
        } catch (e: GHFileNotFoundException) {
            throw BadParameterValue("No issue was found with ID $id")
        }
        if (issue.isPullRequest) throw BadParameterValue("ID $id points to a pull request, not an issue")

        // Require labels
        val labels = issue.labels
        if (!labels.any { it.name.startsWith("T: ") })
            throw UsageError("Issue is missing a type label <T: [feat,fix,...]>")
        if (!labels.any { it.name.startsWith("C: ") })
            throw UsageError("Issue is missing a component label <C: [...]>")

        // Assign
        issue.assignTo(github.myself)

        // Checkout dev-branch for the latest code
        val git = Git.open(prefix)
        call(git.checkout()) {
            setName(BRANCH_DEV)
        }
        call(git.pull())

        // Create branch
        val safeTitle = issue.title
            .replace(Regex("[^a-z0-9]", RegexOption.IGNORE_CASE), "-")
            .toLowerCase()
        val branchName = "issue/$id-$safeTitle"
        call(git.checkout()) {
            setCreateBranch(true)
            setName(branchName)
        }
        git.trackBranch(branchName)
        call(git.push())
    }
}
