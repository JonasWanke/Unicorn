package com.jonaswanke.unicorn.utils

import com.github.ajalt.clikt.output.CliktConsole
import org.beryx.textio.InputReader
import org.beryx.textio.TextIoFactory

object TextIoConsoleWrapper : CliktConsole {
    private val textIo = TextIoFactory.getTextIO()
    private val terminal = textIo.textTerminal
    override val lineSeparator = System.lineSeparator()

    override fun print(text: String, error: Boolean) {
        if (!error) terminal.print(text)
        else terminal.executeWithPropertiesConfigurator({ it.setPromptColor("red") }, {
            it.print(text)
        })
    }

    override fun promptForLine(prompt: String, hideInput: Boolean): String? {
        return textIo.newGenericInputReader { InputReader.ParseResult(it) }
            .withInputMasking(hideInput).read(prompt)
    }
}
