package com.jonaswanke.aluminum.utils

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.UsageError
import com.jonaswanke.aluminum.LABEL_COMPONENT_PREFIX
import com.jonaswanke.aluminum.LABEL_TYPE_PREFIX
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
            if (count { it.name.startsWith(LABEL_TYPE_PREFIX) } != 1)
                throw UsageError("Issue must have exactly one label specifying its type: <$LABEL_TYPE_PREFIX[feat,fix,...]>")
            if (count { it.name.startsWith(LABEL_COMPONENT_PREFIX) } > 1)
                throw UsageError("Issue must have at most one label specifying its component: <$LABEL_COMPONENT_PREFIX[...]>")
        }
    }
}
