package com.jonaswanke.unicorn.core

import java.io.File
import javax.script.ScriptEngineManager

val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")

internal fun readScript(context: RunContext? = null) {
    System.setProperty("idea.use.native.fs.for.win", "false")
    val file = if (context != null) {
        context.projectUnicornDirs.map { it.resolve("unicorn.kts") }
            .firstOrNull { it.exists() } ?: return
    } else {
        listOf(
            File("./unicorn.kts"),
            File("./.unicorn/unicorn.kts")
        ).first { it.exists() }
    }

    scriptEngine.eval(file.bufferedReader())
}
