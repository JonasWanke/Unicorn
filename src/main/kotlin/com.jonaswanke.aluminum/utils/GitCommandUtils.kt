package com.jonaswanke.aluminum.utils

import com.jonaswanke.aluminum.commands.BaseCommand
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
