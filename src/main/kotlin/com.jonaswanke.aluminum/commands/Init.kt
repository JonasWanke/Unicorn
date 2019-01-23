package com.jonaswanke.aluminum.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.jonaswanke.aluminum.BRANCH_DEV
import com.jonaswanke.aluminum.BRANCH_MASTER
import com.jonaswanke.aluminum.REMOTE_DEFAULT
import com.jonaswanke.aluminum.utils.readConfig
import com.jonaswanke.aluminum.utils.trackBranch
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
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

        // Github
        newLine()
        echo("Signing in to GitHub...")
        val (github, cp) = githubAuthenticate(dir)
        val githubRepo = uploadGithub(name, git, github, cp)

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

        copyTemplate(dir, replacements, "gitignore", ".gitignore")
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

    private fun uploadGithub(name: String, git: Git, github: GitHub, cp: CredentialsProvider): GHRepository {
        newLine()
        echo("Uploading to GitHub...")

        val repo = github.createRepository(name).apply {
            description(description)
            autoInit(false)

            issues(true)
            wiki(false)

            allowMergeCommit(true)
            allowRebaseMerge(false)
            allowSquashMerge(false)
        }.create()

        git.remoteAdd()
            .setName(REMOTE_DEFAULT)
            .setUri(URIish(repo.httpTransportUrl))
            .call()
        git.trackBranch(BRANCH_MASTER)
        git.trackBranch(BRANCH_DEV)
        git.push()
            .setCredentialsProvider(cp)
            .setPushAll()
            .call()

        return repo
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
