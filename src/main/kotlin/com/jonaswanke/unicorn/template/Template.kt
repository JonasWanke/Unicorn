package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.ProgramConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.italic
import com.jonaswanke.unicorn.utils.readConfig
import kotlinx.serialization.MissingFieldException
import java.io.File

class Template private constructor(
    val name: String,
    val dir: File,
    val config: TemplateConfig
) {
    companion object {
        const val TEMPLATES_DIR_NAME = "templates"
        const val CONFIG_NAME = ".template.yml"

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

            val config = try {
                File(dir, CONFIG_NAME).readConfig<TemplateConfig>()
            } catch (e: IllegalArgumentException) {
                exit(e.message!!)
            } catch (e: IllegalStateException) {
                exit(e.message!!)
            } catch (e: MissingFieldException) {
                exit(e.message!!)
            }

            Template(name, dir, config)
        }
    }
}
