package com.jonaswanke.unicorn.script

class ConventionalCommit(
    val type: Type,
    scopes: List<String> = emptyList(),
    description: String
) {
    companion object {
        private val MESSAGE_REGEX = ("^\\s*(?<type>${Type.values().joinToString("|") { it.string }})" + // type: "fix"
                "(?:\\((?<scopes>(?:\\w|(?<=\\w)/(?=\\w))+?(?:,\\s*(?:\\w|(?<=\\w)/(?=\\w))+?)*?)\\))?:\\s*" + // scopes: "(commands/init, commands/login): "
                "(?<description>.*?)\\s*\$").toRegex() // message: "login on create"

        fun format(type: Type, scopes: List<String> = emptyList(), description: String): String {
            return ConventionalCommit(type, scopes, description).toString()
        }

        fun parse(message: String): ConventionalCommit {
            val result = MESSAGE_REGEX.matchEntire(message)
                ?: throw IllegalArgumentException("Not a valid conventional commit message: $message")

            val type = result.groups["type"]!!.value
                .let { Type.fromString(it) }
            val scopes = result.groups["scopes"]!!.value
                .split(',')
                .map { scope ->
                    scope.takeUnless { it.isBlank() }
                        ?.trim()
                }
                .filterNotNull()
            val description = result.groups["description"]!!.value
            return ConventionalCommit(type, scopes, description)
        }
    }

    val scopes: List<String> = scopes.map { it.trim() }
        .sorted()
        .apply {
            for (scope in this)
                if (scope.isEmpty())
                    throw IllegalArgumentException("scopes must not be blank, was \"${scopes.joinToString()}\"")
        }
    val description: String = description.trim()
        .apply {
            if (isEmpty()) throw IllegalArgumentException("description must not be blank, was \"$description\"")
        }

    override fun toString() = buildString {
        append(type.string)
        if (scopes.isNotEmpty())
            append("(${scopes.joinToString(",")})")
        append(": ")
        append(description)
    }


    enum class Type(val string: String) {
        CHORE("chore"),
        BUILD("build"),
        DOCS("docs"),
        FEAT("feat"),
        FIX("fix"),
        PERF("perf"),
        UI("ui"),
        REFACTOR("refactor");

        companion object {
            fun fromString(type: String): Type {
                return fromStringOrNull(type)
                    ?: throw IllegalArgumentException("$type is not a conventional commits type")
            }

            fun fromStringOrNull(type: String): Type? {
                return ConventionalCommit.Type.values().firstOrNull {
                    it.string.equals(type, true)
                }
            }
        }
    }
}

fun Git.commit(type: ConventionalCommit.Type, scopes: List<String> = emptyList(), description: String) {
    commit(ConventionalCommit.format(ConventionalCommit.Type.CHORE, description = "bump version"))
}
