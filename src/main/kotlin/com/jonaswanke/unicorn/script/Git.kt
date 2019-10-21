package com.jonaswanke.unicorn.script

import net.swiftzer.semver.SemVer
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer.id
import org.kohsuke.github.GHIssue
import java.io.File
import org.eclipse.jgit.api.Git as ApiGit

class Git(val directory: File = Unicorn.prefix) {
    companion object {
        fun init(directory: File): Git {
            ApiGit.init()
                .setDirectory(directory)
                .call()
            return Git(directory)
        }
    }

    val api by lazy { ApiGit.open(directory) }

    val flow = Flow(this)


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
    fun add(vararg filePattern: String) {
        call(api.add()) {
            filePattern.forEach {
                addFilepattern(it)
            }
        }
    }

    fun commit(message: String) {
        call(api.commit()) {
            setMessage(message)
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
    // endregion

    // region Remote
    fun addRemote(name: String, uri: URIish) {
        call(api.remoteAdd()) {
            setName(name)
            setUri(uri)
        }
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

    fun push(pushAllBranches: Boolean = false, force: Boolean = false) {
        call(api.push()) {
            if (pushAllBranches) setPushAll()
            isForce = force
        }
    }
    // endregion

    // region Helpers
    private fun <C : GitCommand<T>, T> call(command: C, configure: C.() -> Unit = {}): T {
        return command.also {
            if (it is TransportCommand<*, *>)
                it.setCredentialsProvider(GitHub.authenticateOrNull()?.credentialsProvider)
            it.configure()
        }.call()
    }
    // endregion


    class Flow(val git: Git) {
        companion object {
            private const val BRANCH_MASTER_NAME = "master"
            private const val BRANCH_DEV_NAME = "dev"
            const val BRANCH_ISSUE_PREFIX = "issue/"
            const val BRANCH_RELEASE_PREFIX = "release/"
            const val BRANCH_HOTFIX_PREFIX = "hotfix/"
            const val BRANCH_NAME_SEPARATOR = "-"
        }


        class MasterBranch(git: Git) : Branch(git, BRANCH_MASTER_NAME)

        val masterBranch = MasterBranch(git)

        class DevBranch(git: Git) : Branch(git, BRANCH_DEV_NAME)

        val devBranch = Branch(git, BRANCH_DEV_NAME)

        fun currentBranch(gitHub: GitHub): Branch {
            val name = git.currentBranchName
            return when {
                name == BRANCH_MASTER_NAME -> masterBranch
                name == BRANCH_DEV_NAME -> devBranch
                name.startsWith(BRANCH_ISSUE_PREFIX) -> issueIdFromBranchName(name)
                    .let { gitHub.currentRepo().getIssue(it) }
                    .let { IssueBranch(git, it) }
                name.startsWith(BRANCH_RELEASE_PREFIX) -> ReleaseBranch(git, releaseVersionFromBranchName(name))
                name.startsWith(BRANCH_ISSUE_PREFIX) -> HotfixBranch(git, hotfixVersionFromBranchName(name))
                else -> devBranch
            }
        }

        fun checkout(branch: Branch) {
            git.checkout(branch.name)
        }


        // region Issue
        class IssueBranch(git: Git, val issue: GHIssue) : Branch(git, git.flow.branchNameFromIssue(issue))

        fun createIssueBranch(issue: GHIssue): IssueBranch {
            git.createBranch(branchNameFromIssue(issue), base = devBranch.name)
            return IssueBranch(git, issue)
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
        class ReleaseBranch(git: Git, val version: SemVer) : Branch(git, "$BRANCH_RELEASE_PREFIX$version")

        fun createReleaseBranch(version: SemVer): ReleaseBranch {
            if (version <= Unicorn.projectConfig.version)
                throw IllegalArgumentException(
                    "version must be greater than the current version (${Unicorn.projectConfig.version}), was $version"
                )

            git.createBranch(branchNameFromRelease(version), base = devBranch.name)
            return ReleaseBranch(git, version)
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
        class HotfixBranch(git: Git, val version: SemVer) : Branch(git, "$BRANCH_HOTFIX_PREFIX$version")

        fun hotfixVersionFromBranchName(name: String): SemVer {
            if (!name.startsWith(BRANCH_HOTFIX_PREFIX))
                throw IllegalArgumentException("$name is not a hotfix branch")

            return name.removePrefix(BRANCH_HOTFIX_PREFIX)
                .let { SemVer.parse(it) }
        }
        // endregion
    }
}

open class Branch(val git: Git, val name: String) {
    val ref: Ref
        get() = git.api.repository.findRef(name)

    fun checkout() {
        git.checkout(name, false)
    }
}
