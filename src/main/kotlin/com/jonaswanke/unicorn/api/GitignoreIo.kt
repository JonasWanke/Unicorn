package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import okhttp3.OkHttpClient
import okhttp3.Request

object GitignoreIo {
    private const val API_URL = "https://www.gitignore.io/api/"
    private const val ERROR_PREFIX = "#!! ERROR: "

    fun getTemplates(context: RunContext, templateNames: List<String>): String =
        context.group("Fetching templates from gitignore.io") {
            log.i("Templates: ${templateNames.joinToString()}")

            val request = Request.Builder()
                .get()
                .url(API_URL + templateNames.joinToString(","))
                .build()
            log.d("URL: ${request.url()}")

            val result = OkHttpClient().newCall(request).execute().use {
                it.body()?.string()
            } ?: context.exit("Network error: No response")

            val errorLine = result.indexOf(ERROR_PREFIX)
            if (errorLine >= 0) {
                result.substring(errorLine + ERROR_PREFIX.length)
                    .substringBefore(' ')
                    .let { invalidOption -> throw UnknownTemplateException(invalidOption) }
            }

            result
        }

    class UnknownTemplateException(val templateName: String) : Exception("Unknown gitignore.io template: $templateName")
}
