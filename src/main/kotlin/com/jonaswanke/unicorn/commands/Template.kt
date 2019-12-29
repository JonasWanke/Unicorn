package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.template
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.file
import com.jonaswanke.unicorn.script.parameters.flag
import com.jonaswanke.unicorn.script.parameters.option
import com.jonaswanke.unicorn.template.Template
import com.jonaswanke.unicorn.template.Templating
import com.jonaswanke.unicorn.utils.bold
import com.jonaswanke.unicorn.utils.line
import com.jonaswanke.unicorn.utils.list

internal fun Unicorn.registerTemplateCommands() {
    command("template", "t") {
        command("list", "ls") {
            help = "List all available templates"

            run {
                val templates = Template.getAllTemplateNames()
                    .map { Template.getByName(this, it) }
                log.i {
                    list {
                        for (template in templates)
                            line {
                                bold(template.name)
                                if (!template.config.description.isNullOrBlank())
                                    +": ${template.config.description}"
                            }
                    }
                }
            }
        }

        command("apply", "a") {
            help = "Apply a template"

            run(
                argument("name")
                    .template(),
                option("-b", "--base-dir")
                    .file(exists = true, fileOkay = false, writable = true),
                option("-o", "--overwrite")
                    .flag(default = false)
            ) { name, baseDir, overwrite ->
                Templating.applyTemplate(this, name, baseDir ?: projectDir, overwrite)
            }
        }
    }
}
