package com.jonaswanke.aluminum.utils

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.UsageError
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHLabel
import org.kohsuke.github.GHRepository

object GithubIssueUtils {
    fun idFromString(rawId: String): Int {
        return rawId.toIntOrNull()?.takeIf { it > 0 }
            ?: throw BadParameterValue("$rawId is not a valid issue ID")
    }

    fun issueFromId(repo: GHRepository, id: Int): GHIssue {
        val issue = try {
            repo.getIssue(id)
        } catch (e: GHFileNotFoundException) {
            throw BadParameterValue("No issue was found with ID $id")
        }
        if (issue.isPullRequest) throw BadParameterValue("ID $id points to a pull request, not an issue")
        return issue
    }

    fun checkLabels(issue: GHIssue): List<GHLabel> {
        return issue.labels.toList().apply {
            if (!any { it.name.startsWith("T: ") })
                throw UsageError("Issue is missing a type label <T: [feat,fix,...]>")
            if (!any { it.name.startsWith("C: ") })
                throw UsageError("Issue is missing a component label <C: [...]>")
        }
    }
}
