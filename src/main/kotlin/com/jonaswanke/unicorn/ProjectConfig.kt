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
    val labels: Labels = Labels()
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
    data class Labels(
        val components: Components = Components(),
        val types: Types = Types(),
        val list: List<Label> = listOf(
            Label("duplicate", "cfd3d7"),
            Label("wontfix", "cfd3d7"),
            Label("discussion", "d876e3"),
            Label("question", "d876e3")
        )
    ) {
        data class Components(
            val color: String = "c2e0c6",
            val prefix: String = "C: "
        )

        data class Types(
            val color: String = "c5def5",
            val prefix: String = "T: "
        )

        data class Label(
            val name: String,
            val color: String = "cfd3d7",
            val description: String? = null
        )
    }
}
