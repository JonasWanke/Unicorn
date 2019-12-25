package com.jonaswanke.unicorn.core

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.TermUi
import com.jonaswanke.unicorn.utils.*
import java.io.File
import java.io.IOError

abstract class RunContext {
    companion object {
        const val CONFIG_GLOBAL_FILE = "config.yml"
        const val CONFIG_PROJECT_FILE = ".unicorn.yml"
    }

    enum class Environment {
        CONSOLE,
        GITHUB_ACTION;
    }

    abstract val environment: Environment

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

    fun <R> group(name: String, block: RunContext.() -> R): R = copyWithGroup(log.group(name)).block()
    fun <R> group(name: Markup, block: RunContext.() -> R): R = copyWithGroup(log.group(name)).block()
    protected abstract fun copyWithGroup(group: LogCollector.Group): RunContext
    // endregion
}

abstract class InteractiveRunContext : RunContext() {
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
        convert: ((String?) -> String?) = { it }
    ): String? = promptOptional<String?>(
        text,
        optionalText,
        promptSuffix,
        hideInput,
        requireConfirmation,
        confirmationPrompt,
        convert
    )

    fun <T> promptOptional(
        text: String,
        optionalText: String = " (optional)",
        promptSuffix: String = ": ",
        hideInput: Boolean = false,
        requireConfirmation: Boolean = false,
        confirmationPrompt: String = "Repeat for confirmation: ",
        convert: ((String?) -> T?)
    ): T? {
        // Original source: TermUi.prompt
        val prompt = buildPrompt(text + optionalText, promptSuffix, false, null)

        try {
            while (true) {
                val value = TextIoConsole.promptForLine(prompt, hideInput)
                    ?.takeUnless { it.isBlank() }
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
        } catch (err: IOError) {
            return null
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
