package com.jonaswanke.unicorn

data class GlobalConfig(
    val gitHub: GitHubConfig? = null
) {
    data class GitHubConfig(
        val username: String? = null,
        val oauthToken: String? = null,
        val anonymousToken: String? = null,
        val endpoint: String? = null
    )
}
