package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.gitHubRepo
import com.jonaswanke.unicorn.core.ProjectConfig
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
                        projectConfig.types.list.forEach { type ->
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
                if (projectConfig.types.list.any { it.name == name })
                    exit("A type called \"$name\" already exists")

                val newType = ProjectConfig.Types.Type(name, description)
                projectConfig = projectConfig.copy(
                    types = projectConfig.types.copy(
                        list = (projectConfig.types.list + newType).sortedBy { it.name }
                    )
                )
                projectConfig.typeLabelGroup[name]!!.get(gitHubRepo)
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
                    if (projectConfig.types.list.none { it.name == name })
                        exit("Type \"$name\" was not found")

                    projectConfig.typeLabelGroup[name]!!.get(gitHubRepo)
                } else {
                    projectConfig.typeLabelGroup.instances.forEach {
                        log.i {
                            +"Syncing label "
                            kbd(it.name)
                        }
                        it.get(gitHubRepo)
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
                if (projectConfig.types.list.none { it.name == name })
                    exit("Type \"$name\" was not found")

                val oldConfig = projectConfig

                projectConfig = projectConfig.copy(
                    types = projectConfig.types.copy(
                        list = projectConfig.types.list.filter { it.name != name }
                    )
                )

                val oldLabel = oldConfig.typeLabelGroup[name]!!
                if (deleteLabel) oldLabel.getOrNull(gitHubRepo)?.delete()
                else oldLabel.deprecate(gitHubRepo)
            }
        }
    }
}
