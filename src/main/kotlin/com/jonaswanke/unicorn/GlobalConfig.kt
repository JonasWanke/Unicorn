package com.jonaswanke.unicorn

data class GlobalConfig(
    val github: GithubConfig?
) {
    data class GithubConfig(
        val username: String,
        val oauthToken: String,
        val endpoint: String?
    )
}
