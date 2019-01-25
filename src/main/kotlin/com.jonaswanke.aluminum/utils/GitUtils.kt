package com.jonaswanke.aluminum.utils

import com.jonaswanke.aluminum.REMOTE_DEFAULT
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

fun Git.trackBranch(name: String, remoteName: String = "refs/heads/$name", remote: String = REMOTE_DEFAULT) {
    repository.config.apply {
        setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_REMOTE, remote)
        setString(ConfigConstants.CONFIG_BRANCH_SECTION, name, ConfigConstants.CONFIG_KEY_MERGE, remoteName)
    }.save()
}

class OAuthCredentialsProvider(token: String) : UsernamePasswordCredentialsProvider(token, "")
