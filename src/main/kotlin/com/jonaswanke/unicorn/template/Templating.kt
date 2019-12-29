package com.jonaswanke.unicorn.template

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.NoSuchOption
import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.group
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths

typealias TemplateVariables = Map<String, Any?>
typealias MutableTemplateVariables = MutableMap<String, Any?>

object Templating {
    private val configuration = Configuration(Configuration.VERSION_2_3_29).apply {
        setDirectoryForTemplateLoading(Template.templatesDir)

        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
    }
    private const val FTL_SUFFIX = ".ftl"


    fun applyTemplate(
        context: InteractiveRunContext,
        templateName: String,
        baseDir: File = context.projectDir,
        overwriteExisting: Boolean = false,
        parameters: TemplateVariables = emptyMap()
    ): Unit = applyTemplate(context, templateName, baseDir, overwriteExisting, parameters, context.buildInitialData())

    private fun applyTemplate(
        context: InteractiveRunContext,
        templateName: String,
        baseDir: File = context.projectDir,
        overwriteExisting: Boolean = false,
        parameters: TemplateVariables,
        variables: MutableTemplateVariables
    ): Unit = context.group("Applying template $templateName") {
        val template = Template.getByName(this, templateName)

        // Resolve missing parameters
        variables.putAll(parameters)
        template.config.parameters
            .filter { it.id !in parameters.keys }
            .forEach {
                variables[it.id] = it.prompt(this, variables)
            }

        // Apply dependees
        template.config.dependsOn.forEach {
            applyTemplate(
                this,
                it.name,
                File(baseDir, it.baseDir.path),
                overwriteExisting,
                variables + it.evalParameters(variables)
            )
        }

        // Apply own file expansions
        template.config.files
            .filter { it.evalCondition(variables) }
            .forEach { expansion ->
                val from = expansion.evalFrom(this, variables)
                val to = expansion.evalTo(this, variables)

                val files =
                    if (to != null) listOf(from to File(baseDir, to))
                    else {
                        val matcher = FileSystems.getDefault().getPathMatcher("glob:$from")
                        template.dir.walk()
                            .filter { it.isFile }
                            .map { it.relativeTo(template.dir).path }
                            .filter { relativePath -> matcher.matches(Paths.get(relativePath)) }
                            .map { it to File(baseDir, it.removeSuffix(".ftl")) }
                            .toList()
                    }

                files.forEach inner@{ (from, rawTo) ->
                    val isTemplate = expansion.isTemplate ?: from.endsWith(FTL_SUFFIX, ignoreCase = true)
                    val singleTo = if (isTemplate) File(rawTo.path.removeSuffix(FTL_SUFFIX)) else rawTo

                    singleTo.parentFile.mkdirs()
                    if (!overwriteExisting && singleTo.exists()
                        && !confirm("Overwrite ${singleTo.path} with template file $from?")
                    ) return@inner

                    val writer = singleTo.writer()
                    configuration.getTemplate("${template.name}/$from", null, null, isTemplate)
                        .process(variables, writer)
                }
            }
    }

    private fun InteractiveRunContext.buildInitialData(): MutableMap<String, Any?> = mutableMapOf(
        "global" to globalConfig,
        "project" to projectConfig
    )

    private fun TemplateParameter.prompt(
        context: InteractiveRunContext,
        variables: TemplateVariables
    ): Any? {
        val text = buildString {
            append(name)
            if (help != null) {
                append(" (")
                append(help)
                append(")")
            }
        }
        val convert: (String) -> Any = {
            val value = when (this) {
                is TemplateParameter.StringParam -> it
                is TemplateParameter.IntParam -> {
                    it.toIntOrNull() ?: throw BadParameterValue("\"$it\" is not a valid integer")
                }
                is TemplateParameter.EnumParam -> {
                    if (it !in values) throw NoSuchOption(it, values)
                    it
                }
            }
            if (validation?.invoke(value, variables) == false)
                throw BadParameterValue("\"$it\" doesn't satisfy validation")
            it
        }

        val default = default(variables)
        return if (required) context.prompt(text, default, convert = convert)
        else context.promptOptional(text, convert = convert)
    }
}
