package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.utils.code
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

class Git(val directory: File) {
    companion object {
        fun init(directory: File): Git {
            ApiGit.init()
                .setDirectory(directory)
                .call()
            return Git(directory)
        }
    }

    constructor(context: RunContext) : this(context.directory)

    val api by lazy { ApiGit.open(directory) }

    val flow = Flow(this)


    fun checkout(context: RunContext, name: String, createBranch: Boolean = false): Ref {
        context.i {
            code {
                +"git checkout"
                if (createBranch) +" -b"
                +" $name"
            }
        }

        return call(context, api.checkout()) {
            setName(name)
            setCreateBranch(createBranch)
        }
    }

    // region Commits
    fun add(context: RunContext, vararg filePattern: String) {
        context.d { code("git add ${filePattern.joinToString(" ") { "\"$it\"" }}") }
        call(context, api.add()) {
            filePattern.forEach {
                addFilepattern(it)
            }
        }
    }

    fun commit(context: RunContext, message: String) {
        context.i { code { +"git commit -m \"$message\"" } }
        call(context, api.commit()) {
            setMessage(message)
        }
    }
    // endregion

    // region Branching
    val currentBranchName: String
        get() = api.repository.branch

    fun createBranch(
        context: RunContext,
        name: String,
        base: String = Constants.HEAD
    ): Ref {
        @Suppress("NAME_SHADOWING")
        val context = context.group("creating branch $name")

        if (base != Constants.HEAD) checkout(context, base)
        val ref = checkout(context, name, createBranch = true)
        trackBranch(context, name)
        push(context)
        return ref
    }
    // endregion

    // region Remote
    fun listRemotes(context: RunContext): List<RemoteConfig> {
        context.d { code("git remote") }
        return call(context, api.remoteList())
    }

    fun addRemote(context: RunContext, name: String, uri: URIish) {
        context.i { code("git remote add $name $uri") }
        call(context, api.remoteAdd()) {
            setName(name)
            setUri(uri)
        }
    }

    fun trackBranch(
        context: RunContext,
        name: String,
        remoteName: String = "${Constants.R_HEADS}$name",
        remote: String = Constants.DEFAULT_REMOTE_NAME
    ) {
        context.i("git: track branch $name -> $remote:$remoteName")
        api.repository.config.apply {
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_REMOTE, remote)
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_MERGE, remoteName)
        }.save()
    }

    fun push(context: RunContext, pushAllBranches: Boolean = false, force: Boolean = false) {
        context.i {
            code {
                +"git push"
                if (pushAllBranches) +" --all"
                if (force) +" -f"
            }
        }

        call(context, api.push()) {
            if (pushAllBranches) setPushAll()
            isForce = force
        }
    }
    // endregion

    // region Helpers
    private fun <C : GitCommand<T>, T> call(context: RunContext, command: C, configure: C.() -> Unit = {}): T {
        return command.also {
            if (it is TransportCommand<*, *>)
                it.setCredentialsProvider(GitHub.authenticateOrNull(context)?.credentialsProvider)
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

        fun currentBranch(context: RunContext, gitHub: GitHub): Branch {
            val name = git.currentBranchName
            return when {
                name == BRANCH_MASTER_NAME -> masterBranch
                name == BRANCH_DEV_NAME -> devBranch
                name.startsWith(BRANCH_ISSUE_PREFIX) -> issueIdFromBranchName(name)
                    .let { gitHub.currentRepo(context).getIssue(it) }
                    .let { IssueBranch(git, it) }
                name.startsWith(BRANCH_RELEASE_PREFIX) -> ReleaseBranch(git, releaseVersionFromBranchName(name))
                name.startsWith(BRANCH_ISSUE_PREFIX) -> HotfixBranch(git, hotfixVersionFromBranchName(name))
                else -> devBranch
            }
        }

        fun checkout(context: RunContext, branch: Branch) {
            git.checkout(context, branch.name)
        }


        // region Issue
        class IssueBranch(git: Git, val issue: GHIssue) : Branch(git, git.flow.branchNameFromIssue(issue))

        fun createIssueBranch(context: RunContext, issue: GHIssue): IssueBranch {
            @Suppress("NAME_SHADOWING")
            val context = context.group("Git Flow: create issue branch for #${issue.number}")

            git.createBranch(context, branchNameFromIssue(issue), base = devBranch.name)
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

        fun createReleaseBranch(context: RunContext, version: SemVer): ReleaseBranch {
            @Suppress("NAME_SHADOWING")
            val context = context.group("Git Flow: create issue branch for v$version")
            require(version > context.projectConfig.version) {
                "version must be greater than the current version (${context.projectConfig.version}), was $version"
            }

            git.createBranch(context, branchNameFromRelease(version), base = devBranch.name)
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

val RunContext.git: Git
    get() = Git(this)

open class Branch(val git: Git, val name: String) {
    val ref: Ref
        get() = git.api.repository.findRef(name)

    fun checkout(context: RunContext) {
        git.checkout(context, name, false)
    }
}
