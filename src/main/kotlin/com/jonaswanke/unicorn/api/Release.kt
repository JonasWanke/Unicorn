package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.ProjectConfig.CategorizationConfig.TypeConfig.Type.VersionBump
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.*
import net.swiftzer.semver.SemVer
import org.kohsuke.github.GHPullRequest

object Release {
    fun inferNextVersionNumber(context: RunContext, mergedPrs: List<GHPullRequest>): SemVer {
        val baseVersion = context.projectConfig.version

        val types = mergedPrs
            .mapNotNull { it.getType(context)?.value?.versionBump }
            .toSet()

        return when {
            VersionBump.MAJOR in types -> baseVersion.nextMajor
            mergedPrs.any { it.isBreaking } -> baseVersion.nextBreaking
            VersionBump.BREAKING in types -> baseVersion.nextBreaking
            VersionBump.MINOR in types -> baseVersion.nextMinor
            VersionBump.FEATURE in types -> baseVersion.nextFeature
            VersionBump.PATCH in types -> baseVersion.nextPatch
            VersionBump.FIX in types -> baseVersion.nextFix
            else -> baseVersion.nextPatch
        }
    }

    fun getNextVersionNumber(context: InteractiveRunContext, mergedPrs: List<GHPullRequest>): SemVer {
        val current = context.projectConfig.version
        val inferredVersion = inferNextVersionNumber(context, mergedPrs)
        while (true) {
            val version = context.promptSemVer("Version number", default = inferredVersion.toString())
            if (version < current && !context.confirm("Your entered version ($version) is lower than the current project version ($current). Proceed?"))
                continue
            if (version == current && !context.confirm("Your entered version ($version) is the same as the current project version ($current). Proceed?"))
                continue

            return version
        }
    }

    fun generateChangesText(context: RunContext, mergedPrs: List<GHPullRequest>): String =
        context.group("Generating changes text") {
            val projectComponents = context.projectConfig.categorization.component.values.map { it.name }

            var content = buildMarkup {
                for (type in context.projectConfig.categorization.type.values) {
                    val prs = mergedPrs
                        .filter { it.getType(context)?.name == type.name }
                        .map { pr ->
                            pr.getComponents(context)
                                .map { it.name }
                                .sortedBy { projectComponents.indexOf(it) } to pr
                        }
                        .sortedBy { (c, _) -> c.firstOrNull()?.let { projectComponents.indexOf(it) } }
                    if (prs.isEmpty()) continue

                    h3(type.description ?: type.name)
                    newLine()

                    list {
                        for ((components, pr) in prs)
                            line {
                                if (components.isNotEmpty()) {
                                    bold {
                                        +components.joinToString(postfix = ":") { it }
                                    }
                                }

                                +pr.title

                                +" ("
                                link(pr.htmlUrl.toString(), "#${pr.number}")
                                +")"

                                val closedIssues = pr.closedIssues
                                if (closedIssues.isNotEmpty()) {
                                    joined(closedIssues, prefix = ", fixes ") {
                                        link(it.htmlUrl.toString(), "#${it.number}")
                                    }
                                }
                            }
                    }
                    newLine()
                }
            }.toMarkdownString()

            if (context is InteractiveRunContext) {
                content = context.editText(content, extension = ".md")
            }

            return content
        }

    fun titleFromVersion(context: RunContext, version: SemVer): String {
        val current = context.projectConfig.version
        return when {
            version.major > current.major -> ":shipit: $version"
            version.minor > current.minor -> ":rocket: $version"
            else -> version.toString()
        }
    }
}
