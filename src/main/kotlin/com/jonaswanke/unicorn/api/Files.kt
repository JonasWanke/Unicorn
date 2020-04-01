package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.buildMarkup
import com.jonaswanke.unicorn.utils.h2
import com.jonaswanke.unicorn.utils.link
import com.jonaswanke.unicorn.utils.newLine
import net.swiftzer.semver.SemVer
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Files {
    object Changelog {
        const val FILENAME = "CHANGELOG.md"
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")

        fun file(context: RunContext): File = context.projectDir.resolve(FILENAME)

        fun addRelease(context: RunContext, version: SemVer, title: String, changesText: String) {
            val oldVersion = context.gitHubRepo.latestReleaseInclPrerelease

            val entry = buildMarkup {
                h2 {
                    val compareUrl = "${context.gitHubRepo.htmlUrl}/compare/v$oldVersion...v$version"
                    link(compareUrl, title)
                    +" · "
                    +DATE_FORMAT.format(Date())
                }
                newLine()

                +changesText
            }.toMarkdownString()

            val marker = "<!--\\s*newRelease\\s*-->".toRegex(RegexOption.IGNORE_CASE)
            file(context).transformLines { line ->
                writeLine(line)

                if (marker.find(line) != null) {
                    writeLine("<a name=\"$version\"></a>")
                    writeLine(entry)
                }
            }
        }
    }
}


fun File.transformLines(transform: BufferedWriter.(line: String) -> Unit) {
    val tempFile = createTempFile(suffix = ".$extension")

    val bufferedWriter = tempFile.bufferedWriter()
    forEachLine { line ->
        bufferedWriter.transform(line)
    }
    tempFile.copyTo(this, overwrite = true)
}

fun BufferedWriter.writeLine(line: String) {
    write(line)
    newLine()
}
