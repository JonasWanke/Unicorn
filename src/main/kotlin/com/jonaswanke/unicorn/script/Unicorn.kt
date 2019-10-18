package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.core.subcommands
import com.jonaswanke.unicorn.GlobalConfig
import com.jonaswanke.unicorn.ProjectConfig
import com.jonaswanke.unicorn.commands.Commands
import com.jonaswanke.unicorn.script.command.UnicornCommand
import com.jonaswanke.unicorn.utils.readConfig
import com.jonaswanke.unicorn.utils.writeConfig
import java.io.File

@DslMarker
annotation class UnicornMarker


@UnicornMarker
object Unicorn {
    private const val CONFIG_GLOBAL_FILE = "config.yml"
    private const val CONFIG_PROJECT_FILE = ".unicorn.yml"

    lateinit var prefix: File
        internal set

    // region Global config
    private val installDir = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile.parentFile
    private val globalConfigFile: File
        get() = File(installDir, CONFIG_GLOBAL_FILE).apply {
            if (!exists()) {
                createNewFile()
                globalConfig = GlobalConfig(github = null)
            }
        }
    var globalConfig: GlobalConfig
        get() = globalConfigFile.inputStream().readConfig()
        set(value) = globalConfigFile.outputStream().writeConfig(value)
    // endregion

    // region Project config
    fun getProjectConfig(dir: File = prefix): ProjectConfig {
        return File(dir, CONFIG_PROJECT_FILE).inputStream().readConfig()
    }

    fun setProjectConfig(config: ProjectConfig, dir: File = prefix) {
        File(dir, CONFIG_PROJECT_FILE).outputStream().writeConfig(config)
    }

    var projectConfig: ProjectConfig
        get() = getProjectConfig()
        set(value) = setProjectConfig(value)
    // endregion

    // region Commands
    internal fun main(argv: List<String>) {
        Commands.main(argv)
    }

    internal fun addCommand(command: UnicornCommand, aliases: List<String> = emptyList()) {
        Commands.addSubcommand(command, aliases)
    }
    // endregion
}

fun unicorn(unicornBuilder: Unicorn.() -> Unit) {
    Unicorn.unicornBuilder()
}
