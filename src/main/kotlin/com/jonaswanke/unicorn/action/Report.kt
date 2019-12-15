package com.jonaswanke.unicorn.action

import com.jonaswanke.unicorn.commands.LogCollector
import com.jonaswanke.unicorn.commands.Priority
import com.jonaswanke.unicorn.utils.*
import java.io.File

private const val ICON_CHECK = ":heavy_check_mark:"
private const val ICON_INFO = ":information_source:"
private const val ICON_WARNING = ":warning:"
private const val ICON_ERROR = ":x:"

class ReportLogCollector : LogCollector<LogCollector.SimpleGroup> {
    val reportItems = ReportItem.Group(LogCollector.SimpleGroup(this, Markup()))

    override fun log(
        priority: Priority,
        markup: Markup,
        groups: List<LogCollector.SimpleGroup>,
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

    override fun group(name: Markup) = LogCollector.SimpleGroup(this, name)

}

sealed class ReportItem {
    data class Group(val group: LogCollector.SimpleGroup) : ReportItem() {
        val children: MutableList<ReportItem> = mutableListOf()

        fun filter(predicate: (Line) -> Boolean): Group {
            val result = Group(group)
            for (child in children)
                when (child) {
                    is Group -> {
                        val filtered = child.filter(predicate)
                        if (filtered.children.isNotEmpty())
                            result.children += child
                    }
                    is Line -> if (predicate(child)) result.children += child
                }
            return result
        }
    }

    data class Line(
        val priority: Priority,
        val markup: Markup
    ) : ReportItem()
}

@Deprecated("")
sealed class CheckResult {
    companion object {
        fun info(message: String, help: String? = null): CheckResult {
            log(Report.Severity.INFO, message, help)
            return Simple(Report.Severity.INFO, message, help)
        }

        fun warning(message: String, help: String? = null): CheckResult {
            log(Report.Severity.WARNING, message, help)
            return Simple(Report.Severity.WARNING, message, help)
        }

        fun error(message: String, help: String? = null): CheckResult {
            log(Report.Severity.ERROR, message, help)
            return Simple(Report.Severity.ERROR, message, help)
        }

        private fun log(severity: Report.Severity, message: String, help: String? = null) {
            val helpFormatted = help?.let { "Note: $it" }
            when (severity) {
                Report.Severity.INFO -> {
                    println(message)
                    helpFormatted?.let { print(it) }
                }
                Report.Severity.WARNING -> {
                    Action.printWarning(message)
                    helpFormatted?.let { Action.printWarning(it) }
                }
                Report.Severity.ERROR -> {
                    Action.printError(message)
                    helpFormatted?.let { Action.printError(it) }
                }
            }
        }
    }

    abstract val severityCounts: Map<Report.Severity, Int>
    abstract fun appendTo(severity: Report.Severity, builder: StringBuilder)

    data class Simple(
        val severity: Report.Severity,
        val message: String,
        val help: String? = null
    ) : CheckResult() {
        override val severityCounts: Map<Report.Severity, Int>
            get() = mapOf(severity to 1)

        override fun appendTo(severity: Report.Severity, builder: StringBuilder) {
            if (severity != this.severity) return

            with(builder) {
                append("<li>")
                append(message)
                help?.let {
                    appendln("<br>")
                    append("<i>")
                    append(it)
                    append("</i>")
                }
                appendln("</li>")
            }
        }
    }

    data class Group(
        val title: String,
        val results: List<CheckResult>
    ) : CheckResult() {
        override val severityCounts: Map<Report.Severity, Int>
            get() = results.map { it.severityCounts }
                .flatMap { it.entries }
                .groupBy { it.key }
                .mapValues { (_, values) ->
                    values.sumBy { it.value }
                }

        override fun appendTo(severity: Report.Severity, builder: StringBuilder) {
            val relevantResults = results.filter { it.severityCounts[severity] ?: 0 > 0 }
            if (relevantResults.isEmpty()) return

            with(builder) {
                append("<li>")
                append(title)
                appendln(":")

                appendln("<ul>")
                relevantResults.forEach {
                    it.appendTo(severity, this)
                }
                appendln("</ul>")
                appendln("</li>")
            }
        }
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
                if (results == null) return@mapNotNull null

                val title = "${results.children.size} " + when (severity to (results.children.size == 1)) {
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
            .map { it.build() }
            .forEach { +it }

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
