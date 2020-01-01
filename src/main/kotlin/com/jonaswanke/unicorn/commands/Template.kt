package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.template
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.file
import com.jonaswanke.unicorn.script.parameters.flag
import com.jonaswanke.unicorn.script.parameters.option
import com.jonaswanke.unicorn.template.Template
import com.jonaswanke.unicorn.utils.list
import com.jonaswanke.unicorn.utils.resolveTo

internal fun Unicorn.registerTemplateCommands() {
    command("template", "t") {
        help = "Manage file/project templates"

        command("list", "ls") {
            help = "List all available templates"

            run {
                val templates = Template.getAllTemplateNames()
                    .map { Template.getByName(this, it) }
                log.i {
                    list {
                        for (template in templates)
                            template.name
                    }
                }
            }
        }

        command("apply", "a") {
            help = "Apply a template"

            run(
                argument("name", help = "Name of the template")
                    .template(),
                option(
                    "-b", "--base-dir",
                    help = "Base directory in which to apply the template. This defaults to and is interpreted relative to the project directory."
                )
                    .file(exists = true, fileOkay = false, writable = true),
                option(
                    "-o", "--overwrite",
                    help = "Whether to automatically overwrite existing files. Defaults to false."
                )
                    .flag(default = false)
            ) { name, baseDir, overwrite ->
                Template.getByName(this, name)
                    .apply(this, baseDir?.resolveTo(projectDir) ?: projectDir, overwrite)
            }
        }
    }
}
