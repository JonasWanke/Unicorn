package com.jonaswanke.unicorn.script

import net.swiftzer.semver.SemVer
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.transport.RemoteConfig
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer.id
import org.kohsuke.github.GHIssue
import org.eclipse.jgit.api.Git as ApiGit

object Git {
    private val api = ApiGit.open(Unicorn.prefix)

    fun remoteList(): List<RemoteConfig> {
        return call(api.remoteList())
    }

    fun checkout(name: String, createBranch: Boolean = false) {
        call(api.checkout()) {
            setName(name)
            setCreateBranch(createBranch)
        }
    }


    // region Commits
    fun add(message: String) {
        call(api.add()) {

        }
    }
    fun commit(message: String) {
        call(api.commit()) {

        }
    }
    // endregion

    // region Branching
    val currentBranchName: String
        get() = api.repository.branch

    fun createBranch(
        name: String,
        base: String = Constants.HEAD
    ): Ref {
        if (base != Constants.HEAD)
            call(api.checkout()) {
                setName(base)
            }
        val ref = call(api.checkout()) {
            setCreateBranch(true)
            setName(name)
        }
        trackBranch(name)
        call(api.push())
        return ref
    }

    fun trackBranch(
        name: String,
        remoteName: String = "${Constants.R_HEADS}$name",
        remote: String = Constants.DEFAULT_REMOTE_NAME
    ) {
        api.repository.config.apply {
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_REMOTE, remote)
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_MERGE, remoteName)
        }.save()
    }
    // endregion

    // region Helpers
    private fun <C : GitCommand<T>, T> call(command: C, configure: C.() -> Unit = {}): T {
        return command.also {
            if (it is TransportCommand<*, *>)
                it.setCredentialsProvider(GitHub.getIfAuthenticated()?.credentialsProvider)
            it.configure()
        }.call()
    }
    // endregion


    open class Branch(val name: String) {
        val ref: Ref
            get() = api.repository.findRef(name)
    }

    object Flow {
        const val BRANCH_MASTER_NAME = "master"
        const val BRANCH_DEV_NAME = "dev"
        const val BRANCH_ISSUE_PREFIX = "issue/"
        const val BRANCH_RELEASE_PREFIX = "release/"
        const val BRANCH_HOTFIX_PREFIX = "hotfix/"
        const val BRANCH_NAME_SEPARATOR = "-"


        object MasterBranch : Branch(BRANCH_MASTER_NAME)

        object DevBranch : Branch(BRANCH_DEV_NAME)

        fun currentBranch(gitHub: GitHub): Branch {
            val name = currentBranchName
            return when {
                name == BRANCH_MASTER_NAME -> MasterBranch
                name == BRANCH_DEV_NAME -> DevBranch
                name.startsWith(BRANCH_ISSUE_PREFIX) -> issueIdFromBranchName(name)
                    .let { gitHub.requireCurrentRepo.getIssue(it) }
                    .let { IssueBranch(it) }
                name.startsWith(BRANCH_RELEASE_PREFIX) -> ReleaseBranch(releaseVersionFromBranchName(name))
                name.startsWith(BRANCH_ISSUE_PREFIX) -> HotfixBranch(hotfixVersionFromBranchName(name))
                else -> DevBranch
            }
        }


        // region Issue
        class IssueBranch(val issue: GHIssue) : Branch(branchNameFromIssue(issue))

        fun createIssueBranch(issue: GHIssue): IssueBranch {
            createBranch(branchNameFromIssue(issue), base = DevBranch.name)
            return IssueBranch(issue)
        }

        fun branchNameFromIssue(issue: GHIssue): String {
            return issue.title
                .replace(Regex("[^a-z0-9]", RegexOption.IGNORE_CASE), "-")
                .toLowerCase()
                .let { "$BRANCH_ISSUE_PREFIX$id$BRANCH_NAME_SEPARATOR$it" }
        }

        fun issueIdFromBranchName(name: String): Int {
            if (!name.startsWith(BRANCH_ISSUE_PREFIX))
                throw IllegalArgumentException("$name is not an issue branch")

            return name.removePrefix(BRANCH_ISSUE_PREFIX)
                .substringBefore(BRANCH_NAME_SEPARATOR)
                .toInt()
        }
        // endregion

        // region Release
        class ReleaseBranch(version: SemVer) : Branch("$BRANCH_RELEASE_PREFIX$version")

        fun createReleaseBranch(version: SemVer): ReleaseBranch {
            if (version <= Unicorn.projectConfig.version)
                throw IllegalArgumentException(
                    "version must be greater than the current version (${Unicorn.projectConfig.version}), was $version"
                )

            createBranch(branchNameFromRelease(version), base = DevBranch.name)
            return ReleaseBranch(version)
        }

        fun branchNameFromRelease(version: SemVer): String {
            return "$BRANCH_RELEASE_PREFIX$version"
        }

        fun releaseVersionFromBranchName(name: String): SemVer {
            if (!name.startsWith(BRANCH_RELEASE_PREFIX))
                throw IllegalArgumentException("$name is not a release branch")

            return name.removePrefix(BRANCH_RELEASE_PREFIX)
                .let { SemVer.parse(it) }
        }
        // endregion

        // region Hotfix
        class HotfixBranch(version: SemVer) : Branch("$BRANCH_HOTFIX_PREFIX$version")

        fun hotfixVersionFromBranchName(name: String): SemVer {
            if (!name.startsWith(BRANCH_HOTFIX_PREFIX))
                throw IllegalArgumentException("$name is not a hotfix branch")

            return name.removePrefix(BRANCH_HOTFIX_PREFIX)
                .let { SemVer.parse(it) }
        }
        // endregion
    }
}
