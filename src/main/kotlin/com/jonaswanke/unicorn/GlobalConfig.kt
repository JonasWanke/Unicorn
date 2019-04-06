package com.jonaswanke.unicorn

import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

data class GlobalConfig(
    val github: GithubConfig?
) {
    data class GithubConfig(
        val username: String,
        val oauthToken: String,
        val endpoint: String?
    ) {
        fun buildGithub(): GitHub {
            return GitHubBuilder().apply {
                withOAuthToken(oauthToken, username)
                if (endpoint != null) withEndpoint(endpoint)
            }.build()
        }
    }
}
