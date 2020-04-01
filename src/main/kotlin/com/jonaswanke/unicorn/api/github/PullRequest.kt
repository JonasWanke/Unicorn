package com.jonaswanke.unicorn.api.github

import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.core.RunContext
import org.kohsuke.github.GHPullRequest

fun GHPullRequest.addAuthorAsAssignee() {
    // It doesn't seem possible yet to get GitHub accounts of all commit authors, hence we only assign the person who
    // opened this PR for now.
    addAssignees(user)
}

fun GHPullRequest.inferLabels(context: RunContext) {
    val closedIssues = closedIssues

    if (getType(context) == null) {
        val type = closedIssues
            .mapNotNull { it.getType(context) }
            .toSet()
            .singleOrNull()
        if (type != null) setType(context, type)
    }

    inferComponents(context)
        .let { setComponents(context, it) }

    closedIssues.mapNotNull { it.getPriority(context) }.max()
        ?.let { setPriority(context, it) }
}
