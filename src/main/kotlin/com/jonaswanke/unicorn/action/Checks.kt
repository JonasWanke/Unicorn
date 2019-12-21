package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.api.ConventionalCommit
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.api.closedIssues
import com.jonaswanke.unicorn.utils.*
import org.kohsuke.github.GHPullRequest


internal fun runChecks(context: RunContext, reportCollector: ReportLogCollector, pr: GHPullRequest) {
    runTitleCheck(context, reportCollector, pr)
    runClosedIssuesCheck(reportCollector, pr)
    runCommitCheck(reportCollector, pr)
}

private fun runTitleCheck(context: RunContext, reportCollector: ReportLogCollector, pr: GHPullRequest) =
    reportCollector.group("PR title:") {
        val title = try {
            ConventionalCommit.parse(pr.title)
        } catch (e: IllegalArgumentException) {
            e {
                +"doesn't follow "
                link("https://www.conventionalcommits.org/en/v1.0.0") {
                    +"conventional commits"
                }
            }
            return@group
        }
        title.validate(context).let { result ->
            if (!result.isTypeValid) {
                e {
                    +"type "
                    kbd(result.invalidType)
                    +" is invalid"
                    newLine()

                    italic {
                        +"allowed values are: "
                        joined(result.validTypes) { kbd(it) }
                    }
                }
            }
            if (!result.areScopesValid) {
                e {
                    +if (result.invalidScopes.size == 1) "component " else "components "
                    joined(result.invalidScopes) {
                        kbd(it.value)
                        +" (position ${it.index + 1})"
                    }
                    +if (result.invalidScopes.size == 1) " is invalid" else "are invalid"
                    newLine()

                    italic {
                        +"allowed values are: "
                        joined(result.validScopes) { kbd(it) }
                    }
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
