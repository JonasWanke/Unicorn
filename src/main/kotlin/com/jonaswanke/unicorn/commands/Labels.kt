package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.jonaswanke.unicorn.script.GitHub
import com.jonaswanke.unicorn.script.encodedLabelName

class LabelCommand : BaseCommand(name = "label", aliases = listOf("l")) {
    init {
        addSubcommand(CreateCommand())
        addSubcommand(SyncCommand())
    }
}

private class CreateCommand : BaseCommand(
    name = "create",
    aliases = listOf("c"),
    help = "Create a new label"
) {
    companion object {
        const val COLOR_LENGTH = 6
        val COLOR_REGEX = Regex("^[0-9a-fA-F]*$")
        const val COLOR_EXAMPLES = "e.g. '#424242' or 'c0ffee'"
        const val COLOR_DEFAULT = "cfd3d7"
    }

    private val name by argument("name")
    private val color by option(
        "-c", "--color",
        help = "Color of the label (6 hex digits, optionally with '#' in front)"
    )
        .convert { if (it.startsWith('#')) it.substring(1) else it }
        .default(COLOR_DEFAULT)
        .validate {
            require(it.length == COLOR_LENGTH) { "color must be 6 digits long (optionally with '#' in front), $COLOR_EXAMPLES" }
            require(it.matches(COLOR_REGEX)) { "color must consist of hex digits exclusively (optionally with '#' in front), $COLOR_EXAMPLES" }
        }

    override fun execute() {
        val repo = GitHub.authenticate(runContext).currentRepo(runContext)
        val label = repo.createLabel(name.encodedLabelName, color)
        echo("Created label ${label.name} with color #${label.color}")
    }
}

private class SyncCommand :
    BaseCommand(
        name = "sync",
        aliases = listOf("s"),
        help = "Creates all missing type, component and priority labels"
    ) {
    override fun execute() {
        val repo = GitHub.authenticate(runContext).currentRepo(runContext)
        val config = runContext.projectConfig

        val labels = config.typeLabelGroup.instances +
                config.componentsLabelGroup.instances +
                config.priorityLabelGroup.instances
        labels.forEach { it.get(repo) }
    }
}
