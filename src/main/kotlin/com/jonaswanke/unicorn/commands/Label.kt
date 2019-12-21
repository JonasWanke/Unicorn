package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.encodedLabelName
import com.jonaswanke.unicorn.api.gitHubRepo
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.utils.kbd

private const val COLOR_LENGTH = 6
private val COLOR_REGEX = Regex("^[0-9a-fA-F]*$")
private const val COLOR_EXAMPLES = "e.g. '#424242' or 'c0ffee'"
private const val COLOR_DEFAULT = "cfd3d7"

internal fun Unicorn.registerLabelCommands() {
    command("label", "l") {
        command("create", "c") {
            help = "Create a new label"

            run(
                argument("name"),
                option("-c", "--color", help = "Color of the label (6 hex digits, optionally with '#' in front)")
                    .convert { if (it.startsWith('#')) it.substring(1) else it }
                    .default(COLOR_DEFAULT)
                    .validate {
                        require(it.length == COLOR_LENGTH) {
                            "color must be 6 digits long (optionally with '#' in front), $COLOR_EXAMPLES"
                        }
                        require(it.matches(COLOR_REGEX)) {
                            "color must consist of hex digits exclusively (optionally with '#' in front), $COLOR_EXAMPLES"
                        }
                    }
            ) { name, color ->
                val label = gitHubRepo.createLabel(name.encodedLabelName, color)
                i {
                    +"Created label "
                    kbd(label.name)
                    +" with color #${label.color}"
                }
            }
        }

        command("sync", "s") {
            help = "Creates all missing type, component and priority labels"

            run {
                val labels = with(projectConfig) {
                    typeLabelGroup.instances + componentsLabelGroup.instances + priorityLabelGroup.instances
                }
                group("Syncing labels:") {
                    labels.forEach {
                        i {
                            kbd(it.name)
                            +": ${it.description} (#${it.color})"
                        }
                        it.get(gitHubRepo)
                    }
                }
            }
        }
    }
}
