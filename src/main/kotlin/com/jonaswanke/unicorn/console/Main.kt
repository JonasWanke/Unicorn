package com.jonaswanke.unicorn.console

import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.unicorn
import java.io.File
import java.security.Permission
import javax.script.ScriptEngineManager

fun main(args: Array<String>) {
    readScript()
    Unicorn.main(args.asList())
}

private fun readScript() {
    System.setProperty("idea.use.native.fs.for.win", "false")
    val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")
    val file = File("./unicorn.kts")
    scriptEngine.eval(file.bufferedReader())
}