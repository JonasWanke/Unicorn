@file:Suppress("unused")

package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.InteractiveRunContext
import com.jonaswanke.unicorn.core.LogCollector
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.isDescendantOf
import com.jonaswanke.unicorn.utils.resolveTo
import com.jonaswanke.unicorn.utils.withoutExtension
import freemarker.template.TemplateException
import java.io.File
import java.io.FileOutputStream

typealias TemplateVariables = Map<String, Any?>
typealias MutableTemplateVariables = MutableMap<String, Any?>

class TemplateRunContext(
    private val baseContext: InteractiveRunContext,
    val template: Template,
    val baseDir: File,
    val variables: MutableTemplateVariables,
    val overwriteExisting: Boolean
) : InteractiveRunContext() {
    override val environment = baseContext.environment
    override val globalDir = baseContext.globalDir
    override var globalConfig = baseContext.globalConfig
    override val projectDir = baseContext.projectDir
    override var projectConfig = baseContext.projectConfig
    override val log = baseContext.log

    override fun copyWithGroup(group: LogCollector.Group): InteractiveRunContext =
        TemplateRunContext(baseContext.copyWithGroup(group), template, baseDir, variables, overwriteExisting)


    fun applyTemplate(name: String, baseDir: File = File(".")) {
        Template.getByName(this, name)
            .apply(this, File(this.baseDir, baseDir.path), overwriteExisting, variables)
    }

    fun inSubdir(subdir: String, body: TemplateRunContext.() -> Unit) = inSubdir(File(subdir), body)
    @Suppress("NAME_SHADOWING")
    fun inSubdir(subdir: File, body: TemplateRunContext.() -> Unit) {
        val subdir = subdir.normalize()

        if (!subdir.isDescendantOf(baseDir)) {
            log.w("Subdirectory ${subdir.path} is not inside the current base directory ${baseDir}; skipping")
            return
        }

        TemplateRunContext(baseContext, template, baseDir.resolve(subdir), variables.toMutableMap(), overwriteExisting)
            .body()
    }

    // region Copying
    fun copy(
        from: String,
        to: String = from,
        isTemplate: Boolean? = null,
        mode: FileCopyMode = FileCopyMode.OVERWRITE
    ) = copy(File(from), File(to), isTemplate, mode)

    @Suppress("NAME_SHADOWING")
    fun copy(from: File, to: File = from, isTemplate: Boolean? = null, mode: FileCopyMode = FileCopyMode.OVERWRITE) {
        val from = from.normalize()
        var to = to.normalize()
        val isTemplate = isTemplate ?: from.extension == Template.FTL_EXTENSION
        if (isTemplate) to = to.withoutExtension(Template.FTL_EXTENSION)
        log.i("Copying file ${from.path} to ${to.path}")

        if (!to.isDescendantOf(baseDir)) {
            log.w("${to.path} is not inside the current base directory ${baseDir}; skipping")
            return
        }

        val fromAbs = template.resolveFile(from)
        if (!fromAbs.exists()) {
            log.w("Template file ${from.path} not found; skipping")
            return
        }
        if (!fromAbs.isFile) {
            log.w("Template path ${from.path} is not a normal file; skipping")
            return
        }

        val toAbs = baseDir.resolve(to)
        toAbs.parentFile?.mkdirs()

        val exists = toAbs.exists()
        val writer = when (mode) {
            FileCopyMode.KEEP -> {
                if (exists) {
                    log.i("File ${to.path} already exists; skipping")
                    return
                }
                toAbs.writer()
            }
            FileCopyMode.OVERWRITE -> {
                if (exists && !overwriteExisting && !confirm("Overwrite ${to.path} with template file ${from.path}?"))
                    return
                toAbs.writer()
            }
            FileCopyMode.APPEND -> FileOutputStream(toAbs, true).writer()
        }
        val freemarkerTemplate = Template.freemarkerConfiguration
            .getTemplate("${template.name}/${from.path}", null, null, isTemplate)

        try {
            freemarkerTemplate.process(variables, writer)
        } catch (e: TemplateException) {
            log.w("An exception occurred while processing template ${from.path}")
            e.message?.let { log.w(it) }
        }
    }

    fun copyDir(
        from: String,
        to: String = from,
        isTemplate: Boolean? = null,
        mode: FileCopyMode = FileCopyMode.OVERWRITE
    ) = copyDir(File(from), File(to), isTemplate, mode)

    @Suppress("NAME_SHADOWING")
    fun copyDir(from: File, to: File = from, isTemplate: Boolean? = null, mode: FileCopyMode = FileCopyMode.OVERWRITE) {
        val from = from.normalize()
        val to = to.normalize()
        group("Copying directory ${from.path} to ${to.path}") {
            val toAbs = baseDir.resolve(to)
            if (!toAbs.isDescendantOf(baseDir)) {
                log.w("${to.path} is not inside the current base directory ${baseDir}; skipping")
                return
            }

            val fromAbs = template.resolveFile(from)
            if (!fromAbs.exists()) {
                log.w("Template dir ${from.path} not found; skipping")
                return
            }
            if (!fromAbs.isDirectory) {
                log.w("Template path ${from.path} is not a directory; skipping")
                return
            }

            toAbs.parentFile?.mkdirs()
            if (toAbs.exists() && !toAbs.isDirectory) {
                log.w("Project path ${from.path} exists and is not a directory; skipping")
                return
            }

            fromAbs.walk()
                .filter { it.isFile }
                .forEach {
                    val relativeFrom = it.relativeTo(template.dir)
                    val relativeTo = it.relativeTo(fromAbs).resolveTo(to)
                    copy(relativeFrom, relativeTo, isTemplate, mode)
                }
        }
    }
    // endregion
}

enum class FileCopyMode {
    KEEP,
    OVERWRITE,
    APPEND
}
