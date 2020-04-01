package com.jonaswanke.unicorn.core

import java.io.File
import javax.script.ScriptEngineManager

val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")

internal fun readScript(context: RunContext? = null) {
    System.setProperty("idea.use.native.fs.for.win", "false")
    val file = if (context != null) {
        context.projectUnicornDirs.map { it.resolve("unicorn.kts") }
            .firstOrNull { it.exists() } ?: return
    } else File("./unicorn.kts")

    scriptEngine.eval(file.bufferedReader())
}
