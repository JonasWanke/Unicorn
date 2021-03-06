package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.core.LogCollector
import com.jonaswanke.unicorn.core.LogCollector.Priority
import com.jonaswanke.unicorn.utils.*
import java.io.File

private const val ICON_CHECK = ":heavy_check_mark:"
private const val ICON_INFO = ":information_source:"
private const val ICON_WARNING = ":warning:"
private const val ICON_ERROR = ":x:"

class ReportLogCollector : LogCollector {
    val reportItems = ReportItem.Group(LogCollector.Group(this, Markup()))

    override fun log(
        priority: Priority,
        markup: Markup,
        groups: List<LogCollector.Group>,
        file: File?,
        line: Int?,
        col: Int?
    ) {
        val group = groups
            .foldRight(reportItems) { group, position ->
                position.children
                    .filterIsInstance<ReportItem.Group>()
                    .firstOrNull { it.group == group }
                    ?: ReportItem.Group(group).also {
                        position.children += it
                    }
            }
        group.children += ReportItem.Line(priority, markup)
    }
}

sealed class ReportItem {
    abstract val totalLineCount: Int

    data class Group(val group: LogCollector.Group) : ReportItem() {
        val children: MutableList<ReportItem> = mutableListOf()

        override val totalLineCount: Int
            get() = children.sumBy { it.totalLineCount }

        fun filter(predicate: (Line) -> Boolean): Group {
            val result = Group(group)
            for (child in children)
                when (child) {
                    is Group -> {
                        val filtered = child.filter(predicate)
                        if (filtered.children.isNotEmpty()) result.children += child
                    }
                    is Line -> if (predicate(child)) result.children += child
                }
            return result
        }
    }

    data class Line(
        val priority: Priority,
        val markup: Markup
    ) : ReportItem() {
        override val totalLineCount = 1
    }
}

data class Report(
    val checkResults: ReportItem.Group? = null,
    val sections: List<Section> = emptyList()
) {
    companion object {
        val SUFFIX = """
            <p align="right">
              Generated by :unicorn: <a href="https://github.com/JonasWanke/Unicorn">Unicorn</a><br>
              <sub>Report an <a href="https://github.com/JonasWanke/Unicorn/issues">issue</a></sub>
            </p>""".trimIndent()
    }

    val severity: Severity
        get() = createAllSections().map { it.severity }.max() ?: Severity.INFO

    private fun createCheckResultSections(): List<Section> {
        return Severity.values()
            .map { severity ->
                val results = checkResults?.filter {
                    when (severity) {
                        Severity.INFO -> it.priority == Priority.INFO
                        Severity.WARNING -> it.priority == Priority.WARNING
                        Severity.ERROR -> it.priority in listOf(Priority.ERROR, Priority.WTF)
                    }
                }
                severity to results
            }
            .sortedByDescending { (severity, _) -> severity }
            .mapNotNull { (severity, results) ->
                if (results == null || results.children.isEmpty()) return@mapNotNull null

                val totalChildCount = results.totalLineCount
                val title = "$totalChildCount " + when (severity to (totalChildCount == 1)) {
                    Severity.INFO to true -> "Info"
                    Severity.INFO to false -> "Infos"
                    Severity.WARNING to true -> "Warning"
                    Severity.WARNING to false -> "Warnings"
                    Severity.ERROR to true -> "Error"
                    Severity.ERROR to false -> "Errors"
                    else -> throw IllegalStateException("else branch can't be reached")
                }

                fun groupToMarkup(group: ReportItem.Group): Markup = buildMarkup {
                    +group.group.name
                    println("Converting group ${group.group.name}")
                    list {
                        for (child in group.children)
                            when (child) {
                                is ReportItem.Group -> +groupToMarkup(child)
                                is ReportItem.Line -> +child.markup
                            }
                    }
                }

                Section(severity, title, groupToMarkup(results))
            }
    }

    private fun createAllSections(): List<Section> = createCheckResultSections() + sections


    override fun toString() = buildMarkup {
        h2 {
            +(severity.takeUnless { it == Severity.INFO }?.icon ?: ICON_CHECK)
            +" Unicorn Report"
        }

        createAllSections()
            .takeIf { it.isNotEmpty() }
            ?.forEach { +it.build() }
            ?: +"No issues found"

        +SUFFIX
    }.toMarkdownString()

    enum class Severity(val icon: String) {
        INFO(ICON_INFO),
        WARNING(ICON_WARNING),
        ERROR(ICON_ERROR);
    }

    data class Section(
        val severity: Severity,
        val title: String,
        val body: Markup
    ) {
        fun build(): Markup = buildMarkup {
            p {
                h3("${severity.icon} $title")
                +body
            }
        }
    }
}
