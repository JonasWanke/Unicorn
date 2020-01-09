package com.jonaswanke.unicorn.core

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.unicorn.utils.*
import java.io.File

abstract class RunContext {
    companion object {
        const val CONFIG_GLOBAL_FILE = "config.yml"
        const val CONFIG_PROJECT_FILE = ".unicorn.yml"
        const val CONFIG_PROJECT_UNICORN_DIR = ".unicorn"
    }

    enum class Environment {
        CONSOLE,
        GITHUB_ACTION
    }

    abstract val environment: Environment

    // region Global config
    open val globalDir: File? = ProgramConfig.installationDir
    val globalConfigFile: File
        get() = globalDir!!.resolve(CONFIG_GLOBAL_FILE)
    open var globalConfig: GlobalConfig by cached(
        initialGetter = {
            globalConfigFile.takeIf { it.exists() }?.readConfig()
                ?: GlobalConfig()
        },
        setter = { globalConfigFile.writeConfig(it) }
    )
    // endregion

    // region Project config
    abstract val projectDir: File
    open val projectUnicornDir: File
        get() = projectDir.resolve(CONFIG_PROJECT_UNICORN_DIR)
    val projectConfigFile: File
        get() {
            return listOf(
                projectDir.resolve(CONFIG_PROJECT_FILE),
                projectUnicornDir.resolve(CONFIG_PROJECT_FILE)
            ).first { it.exists() }
        }
    open var projectConfig: ProjectConfig by cached(
        initialGetter = { projectConfigFile.readConfig<ProjectConfig>() },
        setter = { projectConfigFile.writeConfig(it) }
    )
    // endregion


    fun exit(message: String): Nothing {
        log.e(message)
        throw CliktError("")
    }

    fun exit(markupBuilder: MarkupBuilder): Nothing {
        log.e(markupBuilder)
        throw CliktError("")
    }

    // region Logging
    abstract val log: LogCollector

    abstract fun copyWithGroup(group: LogCollector.Group): RunContext
    // endregion
}

abstract class InteractiveRunContext : RunContext() {
    abstract override fun copyWithGroup(group: LogCollector.Group): InteractiveRunContext


    fun editText(
        text: String,
        editor: String? = null,
        env: Map<String, String> = emptyMap(),
        requireSave: Boolean = false,
        extension: String = ".txt"
    ): String? = TermUi.editText(text, editor, env, requireSave, extension)

    fun editFile(
        filename: String,
        editor: String? = null,
        env: Map<String, String> = emptyMap(),
        requireSave: Boolean = false,
        extension: String = ".txt"
    ) = TermUi.editFile(filename, editor, env, requireSave, extension)


    fun prompt(
        text: String,
        default: String? = null,
        showDefault: Boolean = true,
        promptSuffix: String = ": ",
        hideInput: Boolean = false,
        requireConfirmation: Boolean = false,
        confirmationPrompt: String = "Repeat for confirmation: ",
        convert: ((String) -> String) = { it }
    ): String = prompt<String>(
        text,
        default,
        showDefault,
        promptSuffix,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        convert
    )

    fun <T> prompt(
        text: String,
        default: String? = null,
        showDefault: Boolean = true,
        promptSuffix: String = ": ",
        hideInput: Boolean = false,
        requireConfirmation: Boolean = false,
        confirmationPrompt: String = "Repeat for confirmation: ",
        convert: ((String) -> T?)
    ): T {
        // Original source: TermUi.prompt
        val prompt = buildPrompt(text, promptSuffix, showDefault, default)

        while (true) {
            var value: String
            while (true) {
                val currentValue = TextIoConsole.promptForLine(prompt, hideInput)

                if (!currentValue.isNullOrBlank()) {
                    value = currentValue
                    break
                    // Skip confirmation prompt if default is used
                } else if (default != null)
                    convert.invoke(default)?.let { return it }
            }
            val result = try {
                convert.invoke(value)
            } catch (err: UsageError) {
                log.e(err.helpMessage())
                continue
            } ?: continue

            if (!requireConfirmation) return result

            var value2: String?
            while (true) {
                value2 = TextIoConsole.promptForLine(confirmationPrompt, hideInput)
                // No need to convert the confirmation, since it is valid if it matches the
                // first value.
                if (!value2.isNullOrBlank()) break
            }
            if (value == value2) return result
            log.w("Error: the two entered values do not match")
        }
    }

    fun promptOptional(
        text: String,
        optionalText: String = " (optional)",
        promptSuffix: String = ": ",
        hideInput: Boolean = false,
        requireConfirmation: Boolean = false,
        confirmationPrompt: String = "Repeat for confirmation: ",
        convert: ((String) -> String?) = { it }
    ): String? = promptOptional<String>(
        text,
        optionalText,
        promptSuffix,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        convert
    )

    fun <T : Any> promptOptional(
        text: String,
        optionalText: String = " (optional)",
        promptSuffix: String = ": ",
        hideInput: Boolean = false,
        requireConfirmation: Boolean = false,
        confirmationPrompt: String = "Repeat for confirmation: ",
        convert: ((String) -> T?)
    ): T? {
        // Original source: TermUi.prompt
        val prompt = buildPrompt(text + optionalText, promptSuffix, false, null)

        while (true) {
            val value = TextIoConsole.promptForLine(prompt, hideInput)
                .takeUnless { it.isNullOrBlank() }
                ?: return null

            val result = try {
                convert.invoke(value)
            } catch (err: UsageError) {
                log.e(err.helpMessage())
                continue
            }

            if (!requireConfirmation) return result

            var value2: String?
            while (true) {
                value2 = TextIoConsole.promptForLine(confirmationPrompt, hideInput)
                // No need to convert the confirmation, since it is valid if it matches the
                // first value.
                if (!value2.isNullOrBlank()) break
            }
            if (value == value2) return result
            log.w("Error: the two entered values do not match")
        }
    }

    private fun buildPrompt(text: String, suffix: String, showDefault: Boolean, default: String?) = buildString {
        append(text)
        if (showDefault && !default.isNullOrBlank())
            append(" [").append(default).append("]")
        append(suffix)
    }

    fun confirm(
        text: String,
        default: Boolean = false,
        abort: Boolean = false,
        promptSuffix: String = ": ",
        showDefault: Boolean = true
    ): Boolean = TermUi.confirm(text, default, abort, promptSuffix, showDefault, TextIoConsole) ?: default
}


@Suppress("UNCHECKED_CAST")
inline fun <C : RunContext, R> C.group(name: String, block: C.() -> R): R =
    (copyWithGroup(log.group(name)) as C).block()

@Suppress("UNCHECKED_CAST")
inline fun <C : RunContext, R> C.group(name: Markup, block: C.() -> R): R =
    (copyWithGroup(log.group(name)) as C).block()
