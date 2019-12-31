package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.gitHubRepo
import com.jonaswanke.unicorn.core.ProjectConfig
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
                        projectConfig.components.forEach { component ->
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
                if (projectConfig.components.any { it.name == name })
                    exit("A component called \"$name\" already exists")

                val newComponent = ProjectConfig.Component(name, description, paths)
                projectConfig = projectConfig.copy(
                    components = (projectConfig.components + newComponent).sortedBy { it.name }
                )
                projectConfig.componentsLabelGroup[name]!!.get(gitHubRepo)
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
                    if (projectConfig.components.none { it.name == name })
                        exit("Component \"$name\" was not found")

                    projectConfig.componentsLabelGroup[name]!!.get(gitHubRepo)
                } else {
                    projectConfig.componentsLabelGroup.instances.forEach {
                        it.get(gitHubRepo)
                    }
                }
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
                if (projectConfig.components.none { it.name == name })
                    exit("Component \"$name\" was not found")

                val oldConfig = projectConfig

                projectConfig = projectConfig.copy(
                    components = projectConfig.components.filter { it.name != name }
                )

                if (deleteLabel)
                    oldConfig.componentsLabelGroup[name]!!.get(gitHubRepo).delete()
            }
        }
    }
}
