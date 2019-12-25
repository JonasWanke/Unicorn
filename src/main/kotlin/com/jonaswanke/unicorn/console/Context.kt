package com.jonaswanke.unicorn.console

import com.github.ajalt.clikt.output.CliktConsole
import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.LogCollector
import com.jonaswanke.unicorn.core.LogCollector.Priority
import com.jonaswanke.unicorn.utils.Markup
import com.jonaswanke.unicorn.utils.TextIoConsole
import java.io.File

class ConsoleRunContext private constructor(
    override var projectDir: File,
    val console: CliktConsole,
    override val log: LogCollector = ConsoleLogCollector(Priority.INFO)
) : InteractiveRunContext() {
    constructor(
        projectDir: File,
        console: CliktConsole,
        minLogPriority: Priority = Priority.INFO
    ) : this(projectDir, console, ConsoleLogCollector(minLogPriority))

    override val environment = Environment.CONSOLE

    override fun copyWithGroup(group: LogCollector.Group) = ConsoleRunContext(projectDir, console, group)
}

class ConsoleLogCollector(
    val minLogPriority: Priority = Priority.INFO
) : LogCollector {
    override fun log(
        priority: Priority,
        markup: Markup,
        groups: List<LogCollector.Group>,
        file: File?,
        line: Int?,
        col: Int?
    ) {
        if (priority < minLogPriority) return

        TermUi.echo(
            markup.toConsoleString(),
            err = priority in listOf(Priority.WARNING, Priority.ERROR, Priority.WTF),
            console = TextIoConsole
        )
    }
}
