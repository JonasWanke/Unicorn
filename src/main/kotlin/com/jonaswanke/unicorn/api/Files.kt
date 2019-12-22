package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import net.swiftzer.semver.SemVer
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Files {
    val MARKER_REGEX = "%1\$s\\s*\\[MARKER (%2\$s)\\]\\s*%3\$s"

    object Changelog {
        const val FILENAME = "CHANGELOG.md"
        val file = File(FILENAME)
        val MARKER_NEW_RELEASE = "newRelease"

        val DATE_FORMAT = SimpleDateFormat("yyyy-mm-dd")

        /*
        fun formatDescription(
            prefixComment: String? = null,
            messages: List<ConventionalCommit> = emptyList(),
            suffixComment: String? = null,
            config: ProjectConfig = Unicorn.projectConfig
        ) = buildString {
            if (!prefixComment.isNullOrBlank()) {
                append(prefixComment)
                repeat(2) { newLine() }
            }

            val typeOrder = config.types.list.withIndex().associate { (index, type) -> type to index }
            if (messages.isNotEmpty()) {
                messages.groupBy { it.type }
                    .toList()
                    .sortedBy { typeOrder[it] }
                    .forEach { (type, commits) ->
                        append("### ")
                        append(it
                            when (type) {
                                ConventionalCommit.Type.CHORE -> "Chore"
                                ConventionalCommit.Type.BUILD -> "Build system changes"
                                ConventionalCommit.Type.DOCS -> "Documentation"
                                ConventionalCommit.Type.FEAT -> "New Features"
                                ConventionalCommit.Type.FIX -> "Bug Fixes"
                                ConventionalCommit.Type.PERF -> "Performance"
                                ConventionalCommit.Type.REFACTOR -> "Refactor"
                                ConventionalCommit.Type.UI -> "UI"
                            }
                        )
                        newLine()
                        newLine()
                        commits
                            .sortedWith(nullsFirst(compareBy { it.scopes.firstOrNull() }))
                            .forEach { commit ->
                                append("- ")
                                if (commit.scopes.isNotEmpty()) {
                                    append("**")
                                    commit.scopes.joinToString()
                                    append(":** ")
                                }
                                append(commit.description)
                            }
                    }
            }

            if (!suffixComment.isNullOrBlank()) {
                repeat(2) { newLine() }
                append(suffixComment)
            }
        }
        */

        fun addRelease(
            context: RunContext,
            version: SemVer,
            name: String? = null,
            date: Date = Date(),
            description: String = ""
        ) {
            file.insert(MARKER_REGEX.format("<!--", MARKER_NEW_RELEASE, "-->").toRegex(RegexOption.IGNORE_CASE)) {
                writeLine("<a name=\"$version\"></a>")
                writeLine(buildString {
                    val fullName = if (name == null) version.toString() else "$version $name"
                    append("## ")
                    val gitHub = GitHub.authenticateOrNull(context)
                    if (gitHub != null) {
                        append("[")
                        append(fullName)
                        append("](")
                        append(gitHub.currentRepo(context).htmlUrl)
                        append("/compare/${context.projectConfig.version}...$version")
                        append(")")
                    }
                    append(" - ")
                    append(DATE_FORMAT.format(date))
                })

                if (description.isNotBlank()) {
                    repeat(2) { newLine() }
                    writeLine(description)
                }

                repeat(3) { newLine() }
            }
        }
    }
}


fun File.insert(marker: Regex, keepMarker: Boolean = true, writer: BufferedWriter.(MatchResult) -> Unit) {
    val tempFile = generateSequence(Triple(0, "${name}_temp", File("${name}_temp"))) { (index, base, file) ->
        if (file.exists()) null
        else {
            val nextIndex = index + 1
            Triple(nextIndex, base, File("$base$nextIndex"))
        }
    }.last()
        .let { (_, _, file) -> file }

    val bufferedWriter = tempFile.bufferedWriter()
    forEachLine { line ->
        val result = marker.find(line)
        if (result != null) {
            if (keepMarker)
                bufferedWriter.write(line)
            bufferedWriter.writer(result)
        } else
            bufferedWriter.writeLine(line)
    }
}

fun BufferedWriter.writeLine(line: String) {
    write(line)
    newLine()
}
