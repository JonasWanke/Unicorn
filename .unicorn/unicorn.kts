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
}
