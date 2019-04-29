package com.jonaswanke.unicorn

import com.jonaswanke.unicorn.commands.Commands
import com.jonaswanke.unicorn.script.*
import com.jonaswanke.unicorn.script.command.*
import java.io.File
import java.security.Permission
import javax.script.ScriptEngineManager

fun main(args: Array<String>) {
    readScript()
    Commands.main(args)
}

private fun readScript() {
    System.setProperty("idea.use.native.fs.for.win", "false")
    val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")
    val file = File("./unicorn.kts")
    scriptEngine.eval(file.bufferedReader())
}
