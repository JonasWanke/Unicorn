package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.ProjectConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.joined
import com.jonaswanke.unicorn.utils.kbd

class ConventionalCommit(
    type: String,
    scopes: List<String> = emptyList(),
    description: String
) {
    companion object {
        private val regex = ("^\\s*(?<type>\\w+)" + // type: "fix"
                "(?:\\((?<scopes>(?:\\w|(?<=\\w)/(?=\\w))+?(?:,\\s*(?:\\w|(?<=\\w)/(?=\\w))+?)*?)\\))?:\\s*" + // scopes: "(commands/init, commands/login): "
                "(?<description>.*?)\\s*\$").toRegex() // message: "login on create"

        fun format(type: String, scopes: List<String> = emptyList(), description: String): String {
            return ConventionalCommit(type, scopes, description).toString()
        }

        fun tryParse(message: String): ConventionalCommit? {
            val result = regex.matchEntire(message) ?: return null

            val type = result.groups["type"]!!.value
            val scopes = (result.groups["scopes"]?.value ?: "")
                .split(',')
                .mapNotNull { scope ->
                    scope.takeUnless { it.isBlank() }
                        ?.trim()
                }
            val description = result.groups["description"]!!.value
            val commit = ConventionalCommit(type, scopes, description)

            return commit
        }

        fun parse(message: String): ConventionalCommit {
            return tryParse(message)
                ?: throw IllegalArgumentException("Not a valid conventional commit message: $message")
        }
    }

    val type: String = type.trim()
        .apply {
            require(isNotEmpty()) { "type must not be blank" }
        }
    val scopes: List<String> = scopes
        .filter { it.isNotBlank() }
        .map { it.trim() }
        .sorted()
    val description: String = description.trim()
        .apply {
            require(!isEmpty()) { "description must not be blank, was \"$description\"" }
        }

    override fun toString() = buildString {
        append(type)
        if (scopes.isNotEmpty())
            append("(${scopes.joinToString(",")})")
        append(": ")
        append(description)
    }


    object Type {
        fun releaseCommit(config: ProjectConfig): String = config.types.releaseCommit
    }

    fun isValid(context: RunContext): Boolean = validate(context).isValid
    fun validate(context: RunContext): ValidationResult {
        // Type
        val validTypes = context.projectConfig.types.list.map { it.name }
        val invalidType = type
            .takeIf { it !in validTypes }
            ?.also {
                context.log.e {
                    +"type "
                    kbd(type)
                    +" is invalid. Allowed values are: "
                    joined(validTypes) { kbd(it) }
                }
            }

        // Scopes
        val validScopes = context.projectConfig.components.map { it.name }
        val invalidScopes = scopes.withIndex()
            .filter { it.value !in validScopes }
            .takeIf { it.isNotEmpty() }
            ?.also { invalidScopes ->
                context.log.e {
                    +if (invalidScopes.size == 1) "component " else "components "
                    joined(invalidScopes) {
                        kbd(it.value)
                        +" (position ${it.index + 1})"
                    }
                    +if (invalidScopes.size == 1) " is invalid" else "are invalid"
                }
            }.orEmpty()

        return ValidationResult(invalidType, validTypes, invalidScopes, validScopes)
    }

    class ValidationResult(
        val invalidType: String?,
        val validTypes: List<String>,
        val invalidScopes: List<IndexedValue<String>>,
        val validScopes: List<String>
    ) {
        val isTypeValid: Boolean
            get() = invalidType == null
        val areScopesValid: Boolean
            get() = invalidScopes.isEmpty()

        val isValid: Boolean
            get() = isTypeValid && areScopesValid
    }
}

fun Git.commit(context: RunContext, type: String, scopes: List<String> = emptyList(), description: String) {
    commit(context, ConventionalCommit.format(type, scopes, description))
}

