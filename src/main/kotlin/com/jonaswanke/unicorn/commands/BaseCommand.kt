package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.utils.TextIoConsoleWrapper
import java.io.File


abstract class BaseCommand(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false
) : CliktCommand(help, epilog, name, invokeWithoutSubcommand) {
    init {
        context {
            console = TextIoConsoleWrapper
        }
    }

    open val prefix by option("--prefix")
        .file(exists = true, fileOkay = false)
        .default(File(System.getProperty("user.dir")))


    }


    override fun run() {
        Unicorn.prefix = prefix
    }
}
