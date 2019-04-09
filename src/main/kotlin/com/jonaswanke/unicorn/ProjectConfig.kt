package com.jonaswanke.unicorn

import net.swiftzer.semver.SemVer

data class ProjectConfig(
    val unicornVersion: SemVer,
    val type: Type,
    val name: String,
    val description: String?,
    val version: SemVer
) {
    enum class Type {
        ANDROID,
        ANGULAR,
        OTHER;

        companion object {
            val stringToValueMap = values()
                .associate { it.name.toLowerCase() to it }
        }
    }
}
