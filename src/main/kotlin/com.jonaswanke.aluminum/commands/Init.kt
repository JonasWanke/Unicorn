package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.jonaswanke.aluminum.BRANCH_DEV
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
open class Create : BaseCommand() {
    private val name by argument("name").optional()
    private val description by argument("description").optional()

    // region Run
    override fun run() {
        val name: String = name
            ?: prompt("What's your project called?")!!
        val description = description
            ?: prompt("Provide a short description", optional = true)

        val replacements = mapOf(
            "NAME" to name,
            "DESCRIPTION" to description,
            "YEAR" to Calendar.getInstance().get(Calendar.YEAR)
        ).mapKeys { "%$it%" }
            .mapValues { it.toString() }

        val dir = File("./$name")
        if (dir.exists()) throw PrintMessage("The specified directory already exists!")
        dir.mkdirs()

        createFiles(dir, replacements)

        val git = initGit(dir, replacements)

        newLine()
        echo("Done!")
    }

    private fun createFiles(dir: File, replacements: Replacements) {
        newLine()
        echo("Copying templates...")

        copyTemplate(dir, replacements, "README.md")
        copyTemplate(dir, replacements, "licenses/Apache License 2.0.txt", "LICENSE")
    }

    private fun initGit(dir: File, replacements: Replacements): Git {
        newLine()
        echo("Initializing git...")

        copyTemplate(dir, replacements, "gitattributes", ".gitattributes")
        val git = Git.init()
            .setDirectory(dir)
            .call()
        git.add()
            .addFilepattern(".")
            .call()
        git.commit()
            .setMessage("Initial commit")
            .call()
        git.checkout()
            .setCreateBranch(true)
            .setName(BRANCH_DEV)
            .call()
        return git
    }
    // endregion


    // region Resources
    private fun copyTemplate(dir: File, replacements: Replacements, resource: String, file: String = resource) {
        File(dir, file).outputStream().bufferedWriter().use { writer ->
            javaClass.getResourceAsStream("/templates/$resource")
                .reader()
                .forEachLine {
                    var line = it
                    for ((short, replacement) in replacements)
                        line = line.replace(short, replacement)
                    writer.write(line)
                    writer.newLine()
                }
        }
    }


    private inline fun <reified T> readConfig(config: String): T {
        return javaClass.getResourceAsStream("/config/$config")
            .readConfig()
    }

    private fun <T> readConfig(config: String, type: TypeReference<T>): T {
        return javaClass.getResourceAsStream("/config/$config")
            .readConfig(type)
    }
    // endregion
}

private typealias Replacements = Map<String, String>
