package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.api.ConventionalCommit
import com.jonaswanke.unicorn.api.closedIssues
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.*
import org.kohsuke.github.GHPullRequest

internal fun runChecks(context: RunContext, reportCollector: ReportLogCollector, pr: GHPullRequest) {
    runLabelsCheck(context, reportCollector, pr)
    runClosedIssuesCheck(reportCollector, pr)
    runCommitCheck(reportCollector, pr)
}

private fun runLabelsCheck(context: RunContext, reportCollector: ReportLogCollector, pr: GHPullRequest) =
    reportCollector.group("PR labels") {
        val typeCategory = context.projectConfig.categorization.type
        val typeLabels = pr.labels
            .filter { it.name.startsWith(typeCategory.labels.prefix) }
            .mapNotNull {
                typeCategory.getOrNull(it.name) ?: {
                    reportCollector.e {
                        +"Unknown type label "
                        kbd(it.name)
                    }
                    null
                }()
            }

        if (typeLabels.size != 1) {
            reportCollector.e {
                +"PR must have exactly one type label, found "
                if (typeLabels.isEmpty()) +"none"
                else joined(typeLabels) {
                    kbd(it.fullName)
                }
            }
        }
    }

private fun runClosedIssuesCheck(reportCollector: ReportLogCollector, pr: GHPullRequest) {
    val closedIssues = pr.closedIssues
    if (closedIssues.isEmpty()) {
        reportCollector.i {
            +"This PR won't close any issues"
            newLine()

            italic {
                +"Reference issues in you PR description using "
                code("Closes: #issueId")
            }
        }
        return
    }

    val closedIssuesString = closedIssues.joinToString { "#${it.number}" }
    reportCollector.i("This PR will close the following issues: $closedIssuesString")
}

private fun runCommitCheck(reportCollector: ReportLogCollector, pr: GHPullRequest) =
    reportCollector.group(buildMarkup {
        +"The following commit messages don't follow "
        link("https://www.conventionalcommits.org/en/v1.0.0", "conventional commits")
        +":"
    }) {
        pr.listCommits()
            .filter { ConventionalCommit.tryParse(it.commit.message) == null }
            .forEach {
                w {
                    code(it.commit.message)
                }
            }
    }
