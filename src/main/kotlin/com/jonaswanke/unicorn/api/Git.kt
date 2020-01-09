package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.code
import com.jonaswanke.unicorn.utils.italic
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHRepository
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

        fun isInitializedIn(directory: File): Boolean {
            if (!directory.isDirectory) return false

            return RepositoryBuilder().apply {
                addCeilingDirectory(directory.parentFile)
                findGitDir(directory)
            }.gitDir != null
        }
    }

    constructor(context: RunContext) : this(context.projectDir)

    val api by lazy { ApiGit.open(directory) }

    val flow = Flow(this)


    fun checkout(context: RunContext, name: String, createBranch: Boolean = false): Ref {
        context.log.i {
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
        context.log.d { code("git add ${filePattern.joinToString(" ") { "\"$it\"" }}") }
        call(context, api.add()) {
            filePattern.forEach {
                addFilepattern(it)
            }
        }
    }

    fun commit(context: RunContext, message: String) {
        context.log.i { code { +"git commit -m \"$message\" --no-gpg-sign" } }
        call(context, api.commit()) {
            setMessage(message)
            setSign(false)
        }
    }

    fun allCommits(context: RunContext): Iterable<RevCommit> {
        return call(context, api.log()) {
            all()
        }
    }

    fun commitsSinceTag(context: RunContext, startTag: String): Iterable<RevCommit> {
        return call(context, api.log()) {
            addRange(
                api.repository.resolve("refs/tags/$startTag"),
                api.repository.resolve(Constants.HEAD)
            )
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
    ): Ref = context.group("creating branch $name") {
        if (base != Constants.HEAD) checkout(context, base)
        val ref = checkout(context, name, createBranch = true)
        trackBranch(context, name)
        push(context)
        ref
    }
    // endregion

    // region Remote
    fun listRemotes(context: RunContext): List<RemoteConfig> {
        context.log.d { code("git remote") }
        return call(context, api.remoteList())
    }

    fun addRemote(context: RunContext, name: String, uri: URIish) {
        context.log.i { code("git remote add $name $uri") }
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
        context.log.i("git: track branch $name -> $remote:$remoteName")
        api.repository.config.apply {
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_REMOTE, remote)
            setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_MERGE, remoteName)
        }.save()
    }

    fun push(context: RunContext, pushAllBranches: Boolean = false, force: Boolean = false) {
        context.log.i {
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
            const val BRANCH_ISSUE_PREFIX = "issue/"
            const val BRANCH_RELEASE_PREFIX = "release/"
            const val BRANCH_HOTFIX_PREFIX = "hotfix/"
            const val BRANCH_NAME_SEPARATOR = "-"
        }


        class MasterBranch(git: Git) : Branch(git, BRANCH_MASTER_NAME)

        val masterBranch = MasterBranch(git)


        fun currentBranch(context: RunContext, gitHubRepo: GHRepository = context.gitHubRepo): Branch {
            val name = git.currentBranchName
            return when {
                name == BRANCH_MASTER_NAME -> masterBranch
                name.startsWith(BRANCH_ISSUE_PREFIX) -> IssueBranch(
                    git,
                    gitHubRepo.getIssue(issueIdFromBranchName(name))
                )
                name.startsWith(BRANCH_RELEASE_PREFIX) -> ReleaseBranch(git, releaseVersionFromBranchName(name))
                name.startsWith(BRANCH_ISSUE_PREFIX) -> HotfixBranch(git, hotfixVersionFromBranchName(name))
                else -> masterBranch
            }
        }

        fun checkout(context: RunContext, branch: Branch) {
            git.checkout(context, branch.name)
        }


        // region Issue
        class IssueBranch(git: Git, val issue: GHIssue) : Branch(git, git.flow.branchNameFromIssue(issue))

        fun createIssueBranch(
            context: RunContext,
            issue: GHIssue,
            gitHubRepo: GHRepository = context.gitHubRepo,
            allowIssueBase: Boolean? = null
        ): IssueBranch = context.group("Git Flow: create issue branch for #${issue.number}") {
            val current = currentBranch(context, gitHubRepo)
            val base = when {
                current is MasterBranch -> current
                current is IssueBranch && allowIssueBase == true -> current
                current is IssueBranch && allowIssueBase == null && this is InteractiveRunContext -> {
                    if (confirm("Base new branch on branch for issue #${current.issue.number}: ${current.issue.title}? (No for master branch)"))
                        current
                    else masterBranch
                }
                current is IssueBranch -> exit {
                    +"You're already on an issue branch ("
                    italic(current.name)
                    +") and basing this new issue branch on an old issue branch was not explicitly allowed."
                }
                else -> exit {
                    +"Current branch "
                    italic(current.name)
                    +" is neither $BRANCH_MASTER_NAME nor an issue branch and hence cannot be used as the base for a new issue branch."
                }
            }

            git.createBranch(context, branchNameFromIssue(issue), base = base.name)
            IssueBranch(git, issue)
        }

        fun branchNameFromIssue(issue: GHIssue): String {
            return issue.title
                .replace(Regex("[^a-z0-9]", RegexOption.IGNORE_CASE), "-")
                .toLowerCase()
                .let { "$BRANCH_ISSUE_PREFIX${issue.number}$BRANCH_NAME_SEPARATOR$it" }
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

        fun createReleaseBranch(context: RunContext, version: SemVer): ReleaseBranch =
            context.group("Git Flow: create release branch for v$version") {
                val currentBranch = currentBranch(context)
                if (currentBranch !is MasterBranch)
                    exit("Cannot create release branch from non-master branch \"$currentBranch\"")

                git.createBranch(context, branchNameFromRelease(version))
                ReleaseBranch(git, version)
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
