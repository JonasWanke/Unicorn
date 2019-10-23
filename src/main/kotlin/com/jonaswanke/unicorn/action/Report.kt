package com.jonaswanke.unicorn.action

private const val ICON_CHECK = ":heavy_check_mark:"
private const val ICON_INFO = ":information_source:"
private const val ICON_WARNING = ":warning:"
private const val ICON_ERROR = ":x:"

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
    val checkResults: List<CheckResult> = emptyList(),
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
                severity to checkResults.filter { it.severityCounts[severity] ?: 0 > 0 }
            }
            .filter { (_, results) -> results.isNotEmpty() }
            .sortedByDescending { (severity, _) -> severity }
            .map { (severity, checks) ->
                val title = "${checks.size} " + when (severity to (checks.size == 1)) {
                    Severity.INFO to true -> "Info"
                    Severity.INFO to false -> "Infos"
                    Severity.WARNING to true -> "Warning"
                    Severity.WARNING to false -> "Warnings"
                    Severity.ERROR to true -> "Error"
                    Severity.ERROR to false -> "Errors"
                    else -> throw IllegalStateException("Else branch can't be reached")
                }
                Section(severity, title) {
                    appendln("<ul>")
                    checks.forEach { check ->
                        check.appendTo(severity, this)
                    }
                    appendln("</ul>")
                }
            }
    }

    private fun createAllSections(): List<Section> = createCheckResultSections() + sections


    override fun toString() = buildString {
        append("## ")
        append(severity.takeUnless { it == Severity.INFO }?.icon ?: ICON_CHECK)
        appendln(" Unicorn Report")
        appendln()

        createAllSections().forEach { it.appendTo(this) }

        append(SUFFIX)
    }

    enum class Severity(val icon: String) {
        INFO(ICON_INFO),
        WARNING(ICON_WARNING),
        ERROR(ICON_ERROR);
    }

    data class Section(
        val severity: Severity,
        val title: String,
        val bodyBuilder: StringBuilder.() -> Unit
    ) {
        companion object {
            fun simple(severity: Severity, title: String, body: String): Section =
                Section(severity, title) { append(body) }
        }

        fun appendTo(builder: StringBuilder) {
            with(builder) {
                append("### ")
                append(severity.icon)
                append(" ")
                appendln(title)
                bodyBuilder()
                appendln()
            }
        }
    }
}
