package com.jonaswanke.unicorn.utils

import com.jonaswanke.unicorn.commands.BaseCommand
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
fun <C : GitCommand<T>, T> BaseCommand.call(command: C, configure: C.() -> Unit = {}) {
    command.also {
        if (it is TransportCommand<*, *>)
            it.setCredentialsProvider(githubCp)
        it.configure()
    }.call()
}

@ExperimentalContracts
fun BaseCommand.createBranch(git: Git, name: String, configure: CheckoutCommand.() -> Unit = {}) {
    call(git.checkout()) {
        setCreateBranch(true)
        setName(name)
        configure()
    }
    git.trackBranch(name)
    call(git.push())
}
