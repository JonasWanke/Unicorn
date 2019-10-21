package com.jonaswanke.unicorn.action

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.ajalt.clikt.core.CliktError
import java.io.File


object Action {
    // region GitHub -> Action
    fun getInput(name: String): String? {
        return System.getenv("INPUT_${name.replace(' ', '_').toUpperCase()}").trim()
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
        return ObjectMapper(JsonFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(Env.githubEventPath, Action.WebhookPayload::class.java)
    }

    data class WebhookPayload(
        @JsonProperty("pull_request")
        val pullRequest: PullRequest? = null
    ) {
        data class PullRequest(
            @JsonProperty("number")
            val number: Int
        )
    }

    // endregion

    // region Action -> GitHub
    fun printWarning(message: String, file: File? = null, line: Int? = null, col: Int? = null) {
        //: :warning file={name},line={line},col={col}::{message}
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
