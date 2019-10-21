package com.jonaswanke.unicorn

import net.swiftzer.semver.SemVer

data class ProjectConfig(
    val unicornVersion: SemVer,
    val type: Type,
    val name: String,
    val description: String? = null,
    val version: SemVer = SemVer(0, 0, 1),
    val types: Types = Types(),
    val components: List<Component> = emptyList(),
) {
    enum class Type {
        ANDROID,
        ANGULAR,
        OTHER;

        companion object {
            val stringToValueMap = values().associateBy { it.name.toLowerCase() }
        }
    }

    data class Types(
        val list: List<String> = listOf(
            "build",
            "chore",
            "ci",
            "docs",
            "feat",
            "fix",
            "perf",
            "refactor"
        ),
        val releaseCommit: String = "chore",
        val feature: String = "feat",
        val fix: String = "fix"
    )

    data class Component(
        val name: String,
        val paths: List<String> = emptyList(),
        val description: String? = null
    )
}
