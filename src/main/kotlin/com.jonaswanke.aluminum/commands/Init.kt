package com.jonaswanke.aluminum.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.jonaswanke.aluminum.BRANCH_DEV
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.*

    private val name by argument("name")
open class Create : BaseCommand() {

    override fun run() {
        val replacements = mapOf(
            "NAME" to name,
            "YEAR" to Calendar.getInstance().get(Calendar.YEAR)
        ).map { (k, v) -> "%$k%" to v.toString() }

        val dir = File("./$name")
        dir.mkdirs()

        fun copyTemplate(resource: String, file: String = resource) {
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

        copyTemplate("README.md")
        copyTemplate("licenses/Apache License 2.0.txt", "LICENSE")

        copyTemplate("gitattributes", ".gitattributes")
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
    }
}
