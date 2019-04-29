package com.jonaswanke.unicorn.script

object ConventionalCommits {
    enum class Type(val string: String) {
        CI("ci"),
        DOCS("docs"),
        FEAT("feat"),
        FIX("fix"),
        PERF("perf"),
        REFACTOR("refactor");

        companion object {
            fun fromString(type: String): Type {
                return fromStringOrNull(type)
                    ?: throw IllegalArgumentException("$type is not a conventional commits type")
            }

            fun fromStringOrNull(type: String): Type? {
                return ConventionalCommits.Type.values().firstOrNull {
                    it.string.equals(type, true)
                }
            }
        }
    }

    fun format(type: Type, scopes: List<String> = emptyList(), description: String): String {
        val filteredScopes = scopes.map { it.trim() }
        for (scope in filteredScopes)
            if (scope.isEmpty())
                throw IllegalArgumentException("scopes must not be blank, was \"${scopes.joinToString()}\"")

        val filteredDescription = description.trim()
        if (filteredDescription.isEmpty()) throw IllegalArgumentException("description must not be blank, was \"$description\"")

        return buildString {
            append(type.string)
            if (filteredScopes.isNotEmpty())
                append("(${filteredScopes.joinToString(",")})")
            append(": ")
            append(description)
        }
    }
}
