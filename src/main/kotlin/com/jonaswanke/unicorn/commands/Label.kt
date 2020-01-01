package com.jonaswanke.unicorn.commands

import com.jonaswanke.unicorn.api.createLabelIfNotExists
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
        help = "Manage labels from GitHub"

        command("create", "c") {
            help = "Create a new label"

            run(
                argument("name", help = "Name of the new label"),
                option("-c", "--color", help = "Color of the label (6 hex digits, optionally with '#' in front)")
                    .convert { it.removePrefix("#") }
                    .default(COLOR_DEFAULT)
                    .validate {
                        require(it.length == COLOR_LENGTH) {
                            "color must be 6 digits long (optionally with '#' in front), $COLOR_EXAMPLES"
                        }
                        require(it.matches(COLOR_REGEX)) {
                            "color must consist of hex digits exclusively (optionally with '#' in front), $COLOR_EXAMPLES"
                        }
                    },
                option("-d", "--description", help = "Description of the label")
            ) { name, color, description ->
                val label = gitHubRepo.createLabelIfNotExists(name, color, description)
                    ?: exit("A label with that name already exists")

                log.i {
                    +"Created label "
                    kbd(label.name)
                    +" with color #${label.color}"
                }
            }
        }
    }
}
