package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.deprecate
import com.jonaswanke.unicorn.api.getGhLabel
import com.jonaswanke.unicorn.api.getGhLabelOrNull
import com.jonaswanke.unicorn.api.gitHubRepo
import com.jonaswanke.unicorn.core.ProjectConfig.CategorizationConfig.PriorityConfig
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.utils.bold
import com.jonaswanke.unicorn.utils.kbd
import com.jonaswanke.unicorn.utils.line
import com.jonaswanke.unicorn.utils.list

fun Unicorn.registerPriorityCommands() {
    command("priority") {
        help = "Manage priorities"

        command("list", "ls") {
            help = "List all priorities of this project"

            run {
                log.i("Priorities (least to most important):")
                log.i {
                    list {
                        projectConfig.categorization.priorities.values.forEach { priority ->
                            line {
                                bold(priority.name)
                                if (priority.description != null)
                                    +" ${priority.description}"
                            }
                        }
                    }
                }
            }
        }

        command("create", "c") {
            help = "Add a priority to this project"

            run(
                argument("name", help = "Name of the new priority")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option("-d", "--desc", "--description", help = "An optional description"),
                option("-i", "--index", help = "0-based index")
                    .int()
                    .default(0)
                    .validate { require(it >= 0) { "Index must be non-negative" } }
            ) { name, description, index ->
                if (name in projectConfig.categorization.priorities)
                    exit("A priority called \"$name\" already exists")

                val newPriority = PriorityConfig.Priority(name, description)
                projectConfig = projectConfig.copyWithCategorizationValues(
                    priorities = projectConfig.categorization.priorities.values.take(index)
                            + newPriority
                            + projectConfig.categorization.priorities.values.drop(index)
                )

                projectConfig.categorization.priorities[name].getGhLabel(gitHubRepo)
            }
        }

        command("sync", "s") {
            help = "Synchronize one or all priority(s) with its/their label(s)"

            run(
                argument("name", help = "Name of the priority to sync")
                    .optional()
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } }
            ) { name ->
                if (name != null) {
                    projectConfig.categorization.priorities.getOrNull(name)
                        ?.getGhLabel(gitHubRepo)
                        ?: exit("Priority \"$name\" was not found")
                } else {
                    projectConfig.categorization.priorities.resolvedValues.forEach {
                        log.i {
                            +"Syncing label "
                            kbd(it.name)
                        }
                        it.getGhLabel(gitHubRepo)
                    }
                }
            }
        }

        command("delete", "d") {
            help = "Delete an existing priority"

            run(
                argument("name", help = "Name of the priority to delete")
                    .validate { require(it.isNotEmpty()) { "Name must not be empty" } },
                option(
                    "--delete-label",
                    help = "Deletes the corresponding label on GitHub. This will also remove the label from all issues and PRs!"
                )
                    .flag(default = false)
            ) { name, deleteLabel ->
                if (name !in projectConfig.categorization.priorities)
                    exit("Priority \"$name\" was not found")

                val oldConfig = projectConfig

                projectConfig = projectConfig.copyWithCategorizationValues(
                    priorities = projectConfig.categorization.priorities.values.filter { it.name != name }
                )

                val oldLabel = oldConfig.categorization.priorities[name]
                if (deleteLabel) oldLabel.getGhLabelOrNull(gitHubRepo)?.delete()
                else oldLabel.deprecate(gitHubRepo)
            }
        }
    }
}
