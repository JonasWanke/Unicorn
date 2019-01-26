package com.jonaswanke.aluminum

import net.swiftzer.semver.SemVer

data class ProjectConfig(
    val aluminumVersion: SemVer,
    val type: Type,
    val name: String,
    val description: String?,
    val version: SemVer,
    val githubName: String? = null
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
