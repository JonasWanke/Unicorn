package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.commands.issue.Issue
import java.io.File
import javax.script.ScriptEngineManager

object Commands : BaseCommand() {
    init {
        subcommands(
            Login(), Logout(),
            Create(),
            Issue()
        )
    }

    override fun run() = Unit
    fun testScripting() {
        System.setProperty("idea.use.native.fs.for.win", "false")
        val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")
        val file = File("./unicorn.kts")
        scriptEngine.eval(file.bufferedReader())
    }
}
