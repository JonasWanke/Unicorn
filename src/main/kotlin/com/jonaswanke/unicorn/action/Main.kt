package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.core.readScript
import kotlin.system.exitProcess


fun main() {
    val context = GitHubActionRunContext()
    readScript(context)

    GitHubAction.body?.invoke(context)

    // Notify GitHub of erroneous status
    if (!GitHubAction.isRunSuccessful) exitProcess(1)
    /*when (context.git.flow.currentBranch(context, repo)) {
        is Git.Flow.MasterBranch -> throwError("Unicorn action is not supported on the master branch")
        is Git.Flow.IssueBranch -> Unit
        is Git.Flow.ReleaseBranch -> {
            context.log.i("Running on a release branch. Exiting")
            return
        }
        is Git.Flow.HotfixBranch -> {
            context.log.w("Hotfixes are not yet supported by Unicorn")
            return
        }
    }*/
}
