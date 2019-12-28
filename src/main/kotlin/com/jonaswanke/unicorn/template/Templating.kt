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

object Templating {
    private val configuration = Configuration(Configuration.VERSION_2_3_29).apply {
        setDirectoryForTemplateLoading(Template.templatesDir)

        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
    }


    fun applyTemplate(
        context: InteractiveRunContext,
        templateName: String,
        baseDir: File = context.projectDir,
        parameters: Map<String, Any?> = emptyMap()
    ): Unit = applyTemplate(context, templateName, baseDir, parameters, context.buildInitialData())

    private fun applyTemplate(
        context: InteractiveRunContext,
        templateName: String,
        baseDir: File = context.projectDir,
        parameters: Map<String, Any?>,
        data: MutableMap<String, Any?>
    ): Unit = context.group("Applying template $templateName") {
        val template = Template.getByName(this, templateName)

        // Resolve missing parameters
        val allParameters = parameters +
                template.config.parameters
                    .filter { it.id !in parameters.keys }
                    .map { it.id to it.prompt(this) }
                    .toMap()
        data.putAll(allParameters)

        // Apply dependees
        template.config.dependsOn.forEach {
            applyTemplate(this, it.name, File(baseDir, it.baseDir.path), data)
        }

        // Apply own file expansions
        template.config.files.forEach { expansion ->
            // TODO: test condition

            val files =
                if (expansion.to != null) listOf(expansion.from to File(baseDir, expansion.to))
                else {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:${expansion.from}")
                    template.dir.walk()
                        .map { it.relativeTo(template.dir).path }
                        .filter { relativePath -> matcher.matches(Paths.get(relativePath)) }
                        .map { it to File(baseDir, it.removeSuffix(".ftl")) }
                        .toList()
                }
            files.forEach { (from, to) ->
                val writer = to.writer()
                configuration.getTemplate(from, null, null, expansion.isTemplate)
                    .process(data, writer)
            }
        }
    }

    private fun InteractiveRunContext.buildInitialData(): MutableMap<String, Any?> = mutableMapOf(
        "user" to mutableMapOf(
            "gitHub" to globalConfig.gitHub?.let {
                mutableMapOf(
                    "name" to it.username
                )
            }
        ),
        "project" to projectConfig
    )

    private fun TemplateParameter.prompt(context: InteractiveRunContext): Any? {
        val text = buildString {
            append(name)
            if (help != null) {
                append(" (")
                append(help)
                append(")")
            }
        }
        val convert: (String) -> Any? = {
            when (this) {
                is TemplateParameter.StringParam -> it
                is TemplateParameter.IntParam -> {
                    it.toIntOrNull() ?: throw BadParameterValue("$it is not a valid integer")
                }
                is TemplateParameter.EnumParam -> {
                    if (it !in values) throw NoSuchOption(it, values)
                    it
                }
            }
        }

        return if (required) context.prompt(text, default, convert = convert)
        else context.promptOptional<Any?>(text) { it?.let { convert(it) } ?: default }
    }
}
