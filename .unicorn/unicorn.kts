import com.jonaswanke.unicorn.action.*
import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.api.github.*
import com.jonaswanke.unicorn.core.*
import com.jonaswanke.unicorn.core.ProjectConfig.*
import com.jonaswanke.unicorn.script.*
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.template.*
import net.swiftzer.semver.SemVer

unicorn {
    gitHubAction {
        val event = this.event
        if (event !is Action.Event.PullRequest) return@gitHubAction

        event.pullRequest.addAuthorAsAssignee()
        event.pullRequest.inferLabels(this)

        runChecks {
            checkLabels()
            checkClosedIssues()
            checkCommitsFollowConventional()
        }
    }

    command("release") {
        help = "Prepare a release"

        run(
            argument("version", help = "Version of this release")
                .semVer()
                .optional()
        ) { rawVersion ->
            val mergedPrs = gitHubRepo.getMergedPrsSinceLastRelease(this)
            val oldVersion = projectConfig.version
            val version = rawVersion ?: Release.getNextVersionNumber(this, mergedPrs)

            val branch = git.flow.createReleaseBranch(this, version)

            val title = Release.titleFromVersion(this, version)
            val changesText = Release.generateChangesText(this, mergedPrs)

            log.i("Writing to changelog")
            Files.Changelog.addRelease(this, version, title, changesText)

            log.i("Changing version numbers")
            projectConfig = projectConfig.copy(unicornVersion = version, version = version)
            projectDir.resolve("src/main/kotlin/com/jonaswanke/unicorn/core/ProgramConfig.kt").transformLines { line ->
                if (line.contains("val VERSION = SemVer("))
                    with(version) {
                        writeLine("    val VERSION = SemVer($major, $minor, $patch, ${preRelease?.let { "\"$it\"" }}, ${buildMetadata?.let { "\"$it\"" }})")
                    }
                else
                    writeLine(line)
            }

            git.add(this, ".")
            git.commit(this, "chore", description = "prepare release")

//            git.push(this)
//            val pr = gitHubRepo.createPullRequest(title, branch.name, git.flow.masterBranch.name, changesText)
//            Desktop.getDesktop().browse(pr.htmlUrl.toURI())
        }
    }
}
