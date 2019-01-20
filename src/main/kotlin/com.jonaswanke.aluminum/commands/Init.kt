package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.output.TermUi
import java.io.File

open class Init : CliktCommand() {
    private val name = File("").absolutePath.substringAfterLast(File.separatorChar)

    override fun run() {
        TermUi.echo(name)
    }
}
