package com.jonaswanke.unicorn.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.jonaswanke.unicorn.api.LabelGroup
import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import net.swiftzer.semver.SemVer

@Serializable
data class ProjectConfig(
    @Serializable(SemVerSerializer::class)
    val unicornVersion: SemVer,
    val name: String,
    val description: String? = null,
    val license: License? = null,
    @Serializable(SemVerSerializer::class)
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
    @Transient
    val typeLabelGroup: LabelGroup = LabelGroup(
        labels.types.color,
        labels.types.prefix,
        labels.types.descriptionPrefix,
        types.list.map { it.name to it.description }
    )
    @Transient
    val componentsLabelGroup: LabelGroup = LabelGroup(
        labels.components.color,
        labels.components.prefix,
        labels.components.descriptionPrefix,
        components.map { it.name to it.description }
    )
    @Transient
    val priorityLabelGroup: LabelGroup = LabelGroup(
        labels.priorities.color,
        labels.priorities.prefix,
        labels.priorities.descriptionPrefix,
        priorities.map { it.name to it.description }
    )

    @Serializable(with = License.Serializer::class)
    enum class License(val keyword: String) {
        NONE("none"),
        APACHE_2_0("apache-2.0"),
        MIT("mit");

        companion object {
            fun fromKeywordOrNull(keyword: String): License? {
                return values().firstOrNull { it.keyword.equals(keyword, ignoreCase = true) }
            }
        }

        override fun toString() = keyword

        @kotlinx.serialization.Serializer(forClass = License::class)
        object Serializer : KSerializer<License> {
            override val descriptor: SerialDescriptor = StringDescriptor

            override fun serialize(encoder: Encoder, obj: License) = encoder.encodeString(obj.keyword)

            override fun deserialize(decoder: Decoder): License {
                val keyword = decoder.decodeString()
                return fromKeywordOrNull(keyword)
                    ?: error(
                        "License $keyword from project config is unknown to Unicorn. " +
                                "Known licenses: ${values().joinToString()}"
                    )
            }
        }
    }

    @Serializable
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
        @Serializable
        data class Type(
            @JsonProperty("name")
            val name: String,
            @JsonProperty("description")
            val description: String? = null
        )
    }

    @Serializable
    data class Component(
        val name: String,
        val description: String? = null,
        val paths: List<String> = emptyList()
    )

    @Serializable
    data class Priority(
        val name: String,
        val description: String? = null
    )

    @Serializable
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
        @Serializable
        data class Components(
            val color: String = "c2e0c6",
            val prefix: String = "C: ",
            val descriptionPrefix: String = "Component: "
        )

        @Serializable
        data class Types(
            val color: String = "c5def5",
            val prefix: String = "T: ",
            val descriptionPrefix: String = "Type: "
        )

        @Serializable
        data class Priorities(
            val color: String = "e5b5ff",
            val prefix: String = "P: ",
            val descriptionPrefix: String = "Priority: "
        )

        @Serializable
        data class Label(
            val name: String,
            val color: String = "cfd3d7",
            val description: String? = null
        )
    }
}
