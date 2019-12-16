package com.jonaswanke.unicorn.console

import com.github.ajalt.clikt.output.CliktConsole
import com.jonaswanke.unicorn.commands.Priority
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.utils.Markup
import com.jonaswanke.unicorn.utils.echo
import java.io.File

class ConsoleRunContext(
    override val projectDir: File,
    val console: CliktConsole,
    val minLogPriority: Priority = Priority.INFO
) : RunContext() {
    override val environment = Environment.CONSOLE

    override fun log(
        priority: Priority,
        markup: Markup,
        groups: List<Group>,
        file: File?,
        line: Int?,
        col: Int?
    ) {
        if (priority < minLogPriority) return

        when (priority) {
            in listOf(Priority.WARNING, Priority.ERROR, Priority.WTF) -> echo(markup.toConsoleString(), err = true)
            else -> echo(markup.toConsoleString())
        }
    }
}
