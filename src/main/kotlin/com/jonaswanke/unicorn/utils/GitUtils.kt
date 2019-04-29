package com.jonaswanke.unicorn.utils

import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class OAuthCredentialsProvider(token: String) : UsernamePasswordCredentialsProvider(token, "")
