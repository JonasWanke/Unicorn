package com.jonaswanke.unicorn.core

import kotlinx.serialization.Serializable

@Serializable
data class GlobalConfig(
    val gitHub: GitHubConfig? = null
) {
    @Serializable
    data class GitHubConfig(
        val username: String? = null,
        val oauthToken: String? = null,
        val anonymousToken: String? = null,
        val endpoint: String? = null
    )
}
