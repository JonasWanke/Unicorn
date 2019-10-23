package com.jonaswanke.unicorn.script

import com.jonaswanke.unicorn.ProjectConfig

class ConventionalCommit(
    type: String,
    scopes: List<String> = emptyList(),
    description: String
) {
    companion object {
        val regex = ("^\\s*(?<type>\\w+)" + // type: "fix"
                "(?:\\((?<scopes>(?:\\w|(?<=\\w)/(?=\\w))+?(?:,\\s*(?:\\w|(?<=\\w)/(?=\\w))+?)*?)\\))?:\\s*" + // scopes: "(commands/init, commands/login): "
                "(?<description>.*?)\\s*\$").toRegex() // message: "login on create"

        fun format(type: String, scopes: List<String> = emptyList(), description: String): String {
            return ConventionalCommit(type, scopes, description).toString()
        }

        fun tryParse(message: String, config: ProjectConfig? = null): ConventionalCommit? {
            // config.types.list.joinToString("|")
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

            config?.let { commit.validate(it) }

            return commit
        }

        fun parse(message: String, config: ProjectConfig? = null): ConventionalCommit {
            return tryParse(message, config)
                ?: throw IllegalArgumentException("Not a valid conventional commit message: $message")
        }
    }

    val type: String = type.trim()
        .apply {
            require(isNotEmpty()) { "type must not be blank" }
        }
    val scopes: List<String> = scopes.map { it.trim() }
        .sorted()
        .apply {
            for (scope in this)
                require(scope.isNotEmpty()) { "scopes must not be blank, was \"${scopes.joinToString()}\"" }
        }
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


    fun validate(config: ProjectConfig): List<ValidationError> {
        val validScopes = config.components.map { it.name }

        return listOfNotNull(
            (type !in config.types.list).thenTake { ValidationError.InvalidType(type) },
            scopes.withIndex()
                .filter { it.value !in validScopes }
                .takeIf { it.isNotEmpty() }
                ?.let { ValidationError.InvalidScopes(it) }
        )
    }

    sealed class ValidationError {
        class InvalidType(val type: String) : ValidationError()
        class InvalidScopes(val scopes: List<IndexedValue<String>>) : ValidationError()
    }
}

private fun <T> Boolean.thenTake(block: () -> T): T? = if (this) block() else null

fun Git.commit(type: String, scopes: List<String> = emptyList(), description: String) {
    commit(ConventionalCommit.format(type, scopes, description))
}

