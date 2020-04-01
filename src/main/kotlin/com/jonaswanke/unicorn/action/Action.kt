package com.jonaswanke.unicorn.action

import com.github.ajalt.clikt.core.CliktError
import com.jonaswanke.unicorn.core.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File


object Action {
    // region GitHub -> Action
    fun getInput(name: String): String? {
        return System.getenv("INPUT_${name.replace(' ', '_').toUpperCase()}")?.trim()
    }

    fun getRequiredInput(name: String): String {
        return getInput(name) ?: throwError("Input required and not supplied: $name")
    }

    object Env {
        val githubRepository: String
            get() = getVariable("GITHUB_REPOSITORY")

        val githubEventPath: File
            get() = File(getVariable("GITHUB_EVENT_PATH"))
        val githubWorkspace: File
            get() = File(getVariable("GITHUB_WORKSPACE"))


        fun getVariable(name: String): String {
            return System.getenv(name)
                ?: throwError("$name not set")
        }
    }

    fun getPayload(): WebhookPayload {
        return json.parse(WebhookPayload.serializer(), Env.githubEventPath.readText())
    }

    @Serializable
    data class WebhookPayload(
        @SerialName("pull_request")
        val pullRequest: PullRequest? = null
    ) {
        @Serializable
        data class PullRequest(
            val number: Int
        )
    }
    // endregion

    // region Action -> GitHub
    fun printWarning(message: String, file: File? = null, line: Int? = null, col: Int? = null) {
        // ::warning file={name},line={line},col={col}::{message}
        println(buildString {
            append("::warning ")
            append(listOfNotNull(
                file?.let { "file=${file.path}" },
                line?.let { "line=$line" },
                col?.let { "col=$col" }
            ).joinToString(","))
            append("::$message")
        })
    }

    fun printError(message: String, file: File? = null, line: Int? = null, col: Int? = null) {
        // ::error file={name},line={line},col={col}::{message}
        println(buildString {
            append("::error ")
            append(listOfNotNull(
                file?.let { "file=${file.path}" },
                line?.let { "line=$line" },
                col?.let { "col=$col" }
            ).joinToString(","))
            append("::$message")
        })
    }

    fun throwError(message: String, file: File? = null, line: Int? = null, col: Int? = null): Nothing {
        printError(message, file, line, col)
        throw CliktError(message)
    }
    // endregion
}
