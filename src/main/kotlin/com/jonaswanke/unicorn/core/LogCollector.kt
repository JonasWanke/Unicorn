@file:Suppress("unused")

package com.jonaswanke.unicorn.core

import com.jonaswanke.unicorn.utils.Markup
import com.jonaswanke.unicorn.utils.MarkupBuilder
import com.jonaswanke.unicorn.utils.buildMarkup
import com.jonaswanke.unicorn.utils.newLine
import java.io.File

interface LogCollector {
    fun newLine(priority: Priority = Priority.INFO) = log(priority, buildMarkup { newLine() })

    fun d(message: String) = d { +message }
    fun d(markupBuilder: MarkupBuilder) = log(Priority.DEBUG, buildMarkup(markupBuilder))
    fun i(message: String) = i { +message }
    fun i(markupBuilder: MarkupBuilder) = log(Priority.INFO, buildMarkup(markupBuilder))
    fun w(message: String) = w { +message }
    fun w(markupBuilder: MarkupBuilder) = log(Priority.WARNING, buildMarkup(markupBuilder))
    fun e(message: String) = e { +message }
    fun e(markupBuilder: MarkupBuilder) = log(Priority.ERROR, buildMarkup(markupBuilder))
    fun wtf(message: String) = wtf { +message }
    fun wtf(markupBuilder: MarkupBuilder) = log(Priority.WTF, buildMarkup(markupBuilder))

    fun log(
        priority: Priority,
        markup: Markup,
        groups: List<Group> = emptyList(),
        file: File? = null,
        line: Int? = null,
        col: Int? = null
    )

    // region Grouping

    fun group(name: String): Group = group(buildMarkup { +name })
    fun group(name: Markup): Group = Group(this, name)

    fun <R> group(name: String, block: Group.() -> R): R = group(name).block()
    fun <R> group(name: Markup, block: Group.() -> R): R = group(name).block()

    data class Group(
        val parent: LogCollector,
        val name: Markup
    ) : LogCollector by parent {
        override fun log(priority: Priority, markup: Markup, groups: List<Group>, file: File?, line: Int?, col: Int?) {
            parent.log(priority, markup, listOf(this) + groups, file, line, col)
        }
    }
    // endregion

    enum class Priority {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        WTF
    }
}
