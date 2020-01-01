package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.deprecate
import com.jonaswanke.unicorn.api.getGhLabel
import com.jonaswanke.unicorn.api.getGhLabelOrNull
import com.jonaswanke.unicorn.api.gitHubRepo
import com.jonaswanke.unicorn.core.ProjectConfig.CategorizationConfig.TypeConfig
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.utils.bold
import com.jonaswanke.unicorn.utils.kbd
import com.jonaswanke.unicorn.utils.line
import com.jonaswanke.unicorn.utils.list

fun Unicorn.registerTypeCommands() {
    command("type") {
        help = "Manage types"

        command("list", "ls") {
            help = "List all types of this project"

            run {
                log.i {
                    list {
                        projectConfig.categorization.types.values.forEach { type ->
                            line {
                                bold(type.name)
                                if (type.description != null)
                                    +" ${type.description}"
                            }
                        }
                    }
                }
            }
        }

        command("create", "c") {
            help = "Add a type to this project"

            run(
                argument("name", help = "Name of the new type")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option("-d", "--desc", "--description", help = "An optional description")
            ) { name, description ->
                if (name in projectConfig.categorization.types)
                    exit("A type called \"$name\" already exists")

                val newType = TypeConfig.Type(name, description)
                projectConfig = projectConfig.copyWithCategorizationValues(
                    types = projectConfig.categorization.types.values + newType
                )

                projectConfig.categorization.types[name].getGhLabel(gitHubRepo)
            }
        }

        command("sync", "s") {
            help = "Synchronize one or all type(s) with its/their label(s)"

            run(
                argument("name", help = "Name of the type to sync")
                    .optional()
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } }
            ) { name ->
                if (name != null) {
                    projectConfig.categorization.types.getOrNull(name)
                        ?.getGhLabel(gitHubRepo)
                        ?: exit("Type \"$name\" was not found")
                } else {
                    projectConfig.categorization.types.resolvedValues.forEach {
                        log.i {
                            +"Syncing label "
                            kbd(it.fullName)
                        }
                        it.getGhLabel(gitHubRepo)
                    }
                }
            }
        }

        command("delete", "d") {
            help = "Delete an existing type"

            run(
                argument("name", help = "Name of the type to delete")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option(
                    "--delete-label",
                    help = "Deletes the corresponding label on GitHub. This will also remove the label from all issues and PRs!"
                )
                    .flag(default = false)
            ) { name, deleteLabel ->
                if (name !in projectConfig.categorization.types)
                    exit("Type \"$name\" was not found")

                val oldConfig = projectConfig

                projectConfig = projectConfig.copyWithCategorizationValues(
                    types = projectConfig.categorization.types.values.filter { it.name != name }
                )

                val oldLabel = oldConfig.categorization.types[name]
                if (deleteLabel) oldLabel.getGhLabelOrNull(gitHubRepo)?.delete()
                else oldLabel.deprecate(gitHubRepo)
            }
        }
    }
}
