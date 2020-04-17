package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import java.io.File
import java.util.*

object Fastlane {
    fun saveChangelog(
        context: RunContext,
        versionCode: Int,
        contents: Map<Locale, String>,
        directory: File = context.projectDir.resolve("fastlane")
    ) {
        val dir = directory.resolve("metadata/android")
        for ((locale, content) in contents) {
            val file = dir.resolve("${locale.toLanguageTag()}/changelogs/$versionCode.txt")
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }
}
