package com.jonaswanke.aluminum.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.jonaswanke.aluminum.BRANCH_DEV
import com.jonaswanke.aluminum.BRANCH_MASTER
import com.jonaswanke.aluminum.REMOTE_DEFAULT
import com.jonaswanke.aluminum.utils.readConfig
import com.jonaswanke.aluminum.utils.trackBranch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHRepository
import java.io.File
import java.util.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
open class Create : BaseCommand() {
    companion object {
        const val GIT_GITIGNOREIO_ERROR_PREFIX = "#!! ERROR: "
        const val GIT_GITIGNORE_FILE = ".gitignore"
        const val GIT_GITATTRIBUTES_FILE = ".gitattributes"
    }

    private val name by argument("name").optional()
    private val description by argument("description").optional()

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

        val webClient = OkHttpClient()

        newLine()


        fun createFiles(): File {
            echo("Creating directory...")
            val dir = File("./$name")
            if (dir.exists()) throw PrintMessage("The specified directory already exists!")
            dir.mkdirs()

            echo("Copying templates...")
            copyTemplate(dir, replacements, "README.md")
            copyTemplate(dir, replacements, "licenses/Apache License 2.0.txt", "LICENSE")
            return dir
        }

        val dir = createFiles()

        // Git
        newLine()
        fun initGit(): Git {
            echo("Initializing git...")

            val gitignore = prompt("Please enter the .gitignore-template names from " +
                    "www.gitignore.io to use (separated by a comma)",
                optional = true, convert = { input ->
                    val templates = input.split(",")
                        .map { it.trim().toLowerCase() }

                    val request = Request.Builder()
                        .get()
                        .url("https://www.gitignore.io/api/${templates.joinToString(",")}")
                        .build()
                    val result = webClient.newCall(request).execute().use {
                        it.body()?.string()
                    } ?: throw CliktError("Network error: No response")

                    val errorLine = result.indexOf(GIT_GITIGNOREIO_ERROR_PREFIX)
                    if (errorLine >= 0)
                        result.substring(errorLine + GIT_GITIGNOREIO_ERROR_PREFIX.length)
                            .substringBefore(' ')
                            .let { invalidOption ->
                                throw NoSuchOption(invalidOption)
                            }

                    result
                })!!
            copyTemplate(dir, replacements, "gitignore", GIT_GITIGNORE_FILE)
            File(dir, GIT_GITIGNORE_FILE)
                .appendText(gitignore)

            copyTemplate(dir, replacements, "gitattributes", GIT_GITATTRIBUTES_FILE)
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

        val git = initGit()

        // Github
        newLine()
        echo("Signing in to GitHub...")
        val (github, cp) = githubAuthenticate(dir)
        fun uploadToGithub(): GHRepository {
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

        val githubRepo = uploadToGithub()
        fun configureGithub() {
            echo("Configuring GitHub...")

            // Labels
            echo("Creating labels")
            for (label in githubRepo.listLabels())
                label.delete()

            val labels = readConfig("github-labels.yaml", object : TypeReference<List<Label>>() {})
            for (label in labels)
                githubRepo.createLabel(label.name, label.color)

            // Branch protection
            echo("Setting up branch protection")
            githubRepo.apply {
                for (branch in listOf(getBranch(BRANCH_MASTER), getBranch(BRANCH_DEV)))
                    branch.enableProtection()
                        .requiredReviewers(1)
                        .includeAdmins(false)
                        .enable()
            }
        }
        configureGithub()

        newLine()
        echo("Done!")
    }

    data class Label(val name: String, val color: String)

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
