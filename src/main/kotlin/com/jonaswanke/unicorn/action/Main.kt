package com.jonaswanke.unicorn.action

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.script.Git
import com.jonaswanke.unicorn.script.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import java.io.File

fun main(args: Array<String>) {
    MainCommand.main(args)
}

private object MainCommand : BaseCommand() {
    override fun run() {
        super.run()
        val repoToken = getRequiredInput("repo-token")

        val gh = GitHub.authenticateWithToken(repoToken)
        val repo = System.getenv("GITHUB_REPOSITORY")
            ?.let { gh.api.getRepository(it) }
            ?: throwError("GITHUB_REPOSITORY not set")
        val projectDir = System.getenv("GITHUB_WORKSPACE")
            ?.let { File(it) }
            ?: throwError("GITHUB_WORKSPACE not set")
        println(projectDir.listFiles()?.joinToString())
        val projectConfig = Unicorn.getProjectConfig(projectDir)

        val eventFile = System.getenv("GITHUB_EVENT_PATH")?.let { File(it) }
            ?: throwError("GITHUB_EVENT_PATH not set")
        println(eventFile.readLines().joinToString())
        val payload = ObjectMapper(JsonFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(eventFile, WebhookPayload::class.java)
        payload.pullRequest ?: throwError("Unicorn currently only supports events of type pull_request")

        val git = Git(prefix)
        val branch = git.flow.currentBranch(gh) as? Git.Flow.IssueBranch
            ?: throwError("Current branch is not a valid issue branch")
        branch.issue
        val pr = repo.getPullRequest(payload.pullRequest.id)
        println(pr)
    }
}

data class WebhookPayload(
    @JsonProperty("pull_request")
    val pullRequest: PullRequest? = null
) {
    data class PullRequest(
        @JsonProperty("number")
        val id: Int,
        @JsonProperty("html_url")
        val htmlUrl: String? = null,
        @JsonProperty("body")
        val body: String? = null
    )
}
