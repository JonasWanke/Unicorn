package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.ProgramConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.ScriptingUtils
import com.jonaswanke.unicorn.utils.italic
import com.jonaswanke.unicorn.utils.list
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File

class Template private constructor(
    val name: String,
    val dir: File,
    val scriptFile: File
) {
    companion object {
        const val TEMPLATES_DIR_NAME = "templates"
        const val SCRIPT_NAME = ".template.kts"

        fun getTemplateDirs(context: RunContext): List<File> {
            return listOfNotNull(
                ProgramConfig.installationDir,
                File(context.projectDir, ".unicorn")
            )
                .map { File(it, TEMPLATES_DIR_NAME) }
                .filter { it.exists() }
        }

        fun getTemplateNames(context: RunContext): List<String> {
            return getTemplateDirs(context)
                .flatMap { it.listFiles()!!.asList() }
                .map { it.name }
                .toSet()
                .sorted()
        }

        fun exists(context: RunContext, name: String) = name in getTemplateNames(context)
        fun getByName(context: RunContext, name: String): Template = context.group("Parsing template $name") {
            getByNameOrNull(context, name)
                ?: exit {
                    +"Template $name not found. Searched directories:"
                    list {
                        getTemplateDirs(this@group).forEach {
                            +it.absolutePath
                        }
                    }
                }
        }

        fun getByNameOrNull(context: RunContext, name: String): Template? = context.group("Parsing template $name") {
            val dir = getTemplateDirs(context)
                .map { File(it, name) }
                .firstOrNull { it.exists() }
                ?: return@group null

            val scriptFile = File(dir, SCRIPT_NAME)
            if (!scriptFile.exists()) exit {
                +"Script file "
                italic(scriptFile.absolutePath)
                +" doesn't exist"
            }

            Template(name, dir, scriptFile)
        }


        const val FTL_EXTENSION = "ftl"
        internal val freemarkerConfiguration = Configuration(Configuration.VERSION_2_3_29).apply {
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
            fallbackOnNullLoopVariable = false
        }
    }


    internal fun resolveFile(relative: File): File = dir.resolve(relative)

    fun apply(
        context: InteractiveRunContext,
        baseDir: File = context.projectDir,
        overwriteExisting: Boolean = false,
        variables: TemplateVariables = context.buildInitialData()
    ) = context.group("Applying template $name") {
        val templateContext =
            TemplateRunContext(this, this@Template, baseDir, variables.toMutableMap(), overwriteExisting)
        ScriptingUtils.eval<Unit>(scriptFile.readText(), templateContext, variables)
    }
}

private fun InteractiveRunContext.buildInitialData(): TemplateVariables = mutableMapOf(
    "global" to globalConfig,
    "project" to projectConfig
)
