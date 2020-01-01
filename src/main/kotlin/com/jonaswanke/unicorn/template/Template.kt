package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.ProgramConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.ScriptingUtils
import com.jonaswanke.unicorn.utils.italic
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

        val templatesDir = File(ProgramConfig.installationDir, TEMPLATES_DIR_NAME)
        fun getAllTemplateNames(): List<String> = templatesDir.listFiles()!!.map { it.name }

        fun exists(name: String) = File(templatesDir, name).exists()
        fun getByName(context: RunContext, name: String): Template = context.group("Parsing template $name") {
            val dir = File(templatesDir, name)
            if (!dir.exists()) exit {
                +"Template not found — directory "
                italic(dir.absolutePath)
                +" doesn't exist"
            }

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
            setDirectoryForTemplateLoading(templatesDir)

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
