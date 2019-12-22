package com.jonaswanke.unicorn.core

import com.github.ajalt.clikt.core.CliktError
import com.jonaswanke.unicorn.utils.*
import java.io.File

interface LogCollector<G : LogCollector.Group<G>> {
    fun d(message: String) = d { +message }
    fun d(markupBuilder: MarkupBuilder) = log(Priority.DEBUG, buildMarkup(markupBuilder))
    fun i(message: String) = i { +message }
    fun i(markupBuilder: MarkupBuilder) = log(Priority.INFO, buildMarkup(markupBuilder))
    fun w(message: String) = w { +message }
    fun w(markupBuilder: MarkupBuilder) = log(Priority.WARNING, buildMarkup(markupBuilder))
    fun e(message: String) = e { +message }
    fun e(markupBuilder: MarkupBuilder) = log(Priority.ERROR, buildMarkup(markupBuilder))
    fun exit(message: String): Nothing = exit { +message }
    fun exit(markupBuilder: MarkupBuilder): Nothing {
        log(Priority.ERROR, buildMarkup(markupBuilder))
        throw CliktError()
    }
    fun wtf(message: String) = wtf { +message }
    fun wtf(markupBuilder: MarkupBuilder) = log(Priority.WTF, buildMarkup(markupBuilder))

    fun log(
        priority: Priority,
        markup: Markup,
        groups: List<G> = emptyList(),
        file: File? = null,
        line: Int? = null,
        col: Int? = null
    )

    // region Grouping
    fun group(name: String): G = group(buildMarkup { +name })

    fun group(name: Markup): G

    fun <R> group(name: String, block: G.() -> R): R = group(name).block()
    fun <R> group(name: Markup, block: G.() -> R): R = group(name).block()

    interface Group<G : Group<G>> :
        LogCollector<G> {
        val parent: LogCollector<G>
        val name: Markup

        override fun log(priority: Priority, markup: Markup, groups: List<G>, file: File?, line: Int?, col: Int?) {
            @Suppress("UNCHECKED_CAST")
            parent.log(priority, markup, listOf(this as G) + groups, file, line, col)
        }
    }

    open class SimpleGroup(
        override val parent: LogCollector<SimpleGroup>,
        override val name: Markup
    ) : Group<SimpleGroup> {
        override fun group(name: Markup) = SimpleGroup(this, name)
    }
    // endregion
}

abstract class RunContext : LogCollector<RunContext.Group> {
    companion object {
        const val CONFIG_GLOBAL_FILE = "config.yml"
        const val CONFIG_PROJECT_FILE = ".unicorn.yml"
    }

    enum class Environment {
        CONSOLE,
        GITHUB_ACTION;
    }

    abstract val environment: Environment
    open val isInteractive: Boolean
        get() = when (environment) {
            Environment.CONSOLE -> true
            Environment.GITHUB_ACTION -> false
        }

    open val directory: File = File(System.getProperty("user.dir"))

    // region Global config
    open val globalDir: File? = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile?.parentFile
    val globalConfigFile: File
        get() = File(globalDir, CONFIG_GLOBAL_FILE)
    open var globalConfig: GlobalConfig by cached(
        initialGetter = {
            globalConfigFile.takeIf { it.exists() }
                ?.inputStream()
                ?.readConfig<GlobalConfig>()
                ?: GlobalConfig()
        },
        setter = { globalConfigFile.outputStream().writeConfig(it) }
    )
    // endregion

    // region Project config
    abstract val projectDir: File
    val projectConfigFile: File
        get() = File(projectDir, CONFIG_PROJECT_FILE)
    open var projectConfig: ProjectConfig by cached(
        initialGetter = { projectConfigFile.inputStream().readConfig<ProjectConfig>() },
        setter = { projectConfigFile.outputStream().writeConfig(it) }
    )
    // endregion

    override fun group(name: Markup): Group =
        Group(this, name)

    class Group(
        override val parent: RunContext,
        override val name: Markup
    ) : RunContext(),
        LogCollector.Group<Group> {
        override val environment by parent::environment
        override val isInteractive by parent::isInteractive
        override val globalDir by parent::globalDir
        override var globalConfig by parent::globalConfig
        override val projectDir by parent::projectDir
        override var projectConfig by parent::projectConfig
    }
}

enum class Priority {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    WTF
}
