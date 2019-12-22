package com.jonaswanke.unicorn.core

import com.jonaswanke.unicorn.api.LabelGroup
import net.swiftzer.semver.SemVer

data class ProjectConfig(
    val unicornVersion: SemVer,
    val name: String,
    val description: String? = null,
    val version: SemVer = SemVer(0, 0, 1),
    val types: Types = Types(),
    val components: List<Component> = emptyList(),
    val priorities: List<Priority> = listOf(
        Priority("1", "1 (Lowest)"),
        Priority("2", "2 (Low)"),
        Priority("3", "3 (Medium)"),
        Priority("4", "4 (High)"),
        Priority("5", "5 (Highest)")
    ),
    val labels: Labels = Labels()
) {
    val typeLabelGroup: LabelGroup = LabelGroup(
        labels.types.color,
        labels.types.prefix,
        labels.priorities.descriptionPrefix,
        types.list.map { it.name to it.description }
    )
    val componentsLabelGroup: LabelGroup = LabelGroup(
        labels.components.color,
        labels.components.prefix,
        labels.components.descriptionPrefix,
        components.map { it.name to it.description }
    )
    val priorityLabelGroup: LabelGroup = LabelGroup(
        labels.priorities.color,
        labels.priorities.prefix,
        labels.priorities.descriptionPrefix,
        priorities.map { it.name to it.description }
    )

    data class Types(
        val list: List<Type> = listOf(
            Type("build", "Build changes"),
            Type("chore", "Chores"),
            Type("ci", "CI changes"),
            Type("docs", "Documentation updates"),
            Type("feat", "New Features"),
            Type("fix", "Bugfixes"),
            Type("perf", "Performance improvements"),
            Type("refactor", "Refactoring")
        ),
        val releaseCommit: String = "chore",
        val feature: String = "feat",
        val fix: String = "fix"
    ) {
        data class Type(
            val name: String,
            val description: String? = null
        )
    }

    data class Component(
        val name: String,
        val paths: List<String> = emptyList(),
        val description: String? = null
    )

    data class Priority(
        val name: String,
        val description: String? = null
    )

    data class Labels(
        val components: Components = Components(),
        val types: Types = Types(),
        val priorities: Priorities = Priorities(),
        val list: List<Label> = listOf(
            Label("duplicate", "cfd3d7"),
            Label("wontfix", "cfd3d7"),
            Label("discussion", "d876e3"),
            Label("question", "d876e3")
        )
    ) {
        data class Components(
            val color: String = "c2e0c6",
            val prefix: String = "C: ",
            val descriptionPrefix: String = "Component: "
        )

        data class Types(
            val color: String = "c5def5",
            val prefix: String = "T: ",
            val descriptionPrefix: String = "Type: "
        )

        data class Priorities(
            val color: String = "e5b5ff",
            val prefix: String = "P: ",
            val descriptionPrefix: String = "Priority: "
        )

        data class Label(
            val name: String,
            val color: String = "cfd3d7",
            val description: String? = null
        )
    }
}
