package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.template.Template
import com.jonaswanke.unicorn.template.Templating
import org.jetbrains.kotlin.utils.keysToMap

internal fun Unicorn.registerTemplateCommands() {
    command("template", "t") {
        help = "Apply a template"

        run(
            argument("name")
                .choice(Template.getAllTemplateNames().keysToMap { it }),
            option("-b", "--base-dir")
                .file(exists = true, fileOkay = false, writable = true),
            option("-o", "--overwrite")
                .flag(default = false)
        ) { name, baseDir, overwrite ->
            Templating.applyTemplate(this, name, baseDir ?: projectDir)
        }
    }
}
