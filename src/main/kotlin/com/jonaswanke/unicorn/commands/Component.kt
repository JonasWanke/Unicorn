package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.core.ProjectConfig.CategorizationConfig.ComponentConfig
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.utils.bold
import com.jonaswanke.unicorn.utils.line
import com.jonaswanke.unicorn.utils.list

fun Unicorn.registerComponentCommands() {
    command("component") {
        help = "Manage components - usually thematically separated parts of the project."

        command("list", "ls") {
            help = "List all components of this project"

            run {
                log.i {
                    list {
                        projectConfig.categorization.component.values.forEach { component ->
                            line {
                                bold(component.name)
                                if (component.description != null)
                                    +" ${component.description}"
                            }
                        }
                    }
                }
            }
        }

        command("create", "c") {
            help = "Add a component to this project"

            run(
                argument("name", help = "Name of the new component")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option("-d", "--desc", "--description", help = "An optional description"),
                option("-p", "--path", help = "Paths to e.g. detect components in PR changes (supports glob)")
                    .multiple()
            ) { name, description, paths ->
                if (name in projectConfig.categorization.component)
                    exit("A component called \"$name\" already exists")

                val newComponent = ComponentConfig.Component(name, description, paths)
                projectConfig = projectConfig.copyWithCategorizationValues(
                    components = projectConfig.categorization.component.values + newComponent
                )

                projectConfig.categorization.component[name].getGhLabel(gitHubRepo)
            }
        }

        command("sync", "s") {
            help = "Synchronize one or all component(s) with its/their label(s)"

            run(
                argument("name", help = "Name of the component to sync")
                    .optional()
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } }
            ) { name ->
                if (name != null) {
                    projectConfig.categorization.component.getOrNull(name)
                        ?.getGhLabel(gitHubRepo)
                        ?: exit("Component \"$name\" was not found")
                } else gitHubRepo.syncComponentLabels(this)
            }
        }

        command("delete", "d") {
            help = "Delete an existing component"

            run(
                argument("name", help = "Name of the component to delete")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option(
                    "--delete-label",
                    help = "Deletes the corresponding label on GitHub. This will also remove the label from all issues and PRs!"
                )
                    .flag(default = false)
            ) { name, deleteLabel ->
                if (name !in projectConfig.categorization.component)
                    exit("Component \"$name\" was not found")

                val oldConfig = projectConfig

                projectConfig = projectConfig.copyWithCategorizationValues(
                    components = projectConfig.categorization.component.values.filter { it.name != name }
                )

                val oldLabel = oldConfig.categorization.component[name]
                if (deleteLabel) oldLabel.getGhLabelOrNull(gitHubRepo)?.delete()
                else oldLabel.deprecate(gitHubRepo)
            }
        }
    }
}
