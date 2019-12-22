package com.jonaswanke.unicorn.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jonaswanke.unicorn.api.Git
import com.jonaswanke.unicorn.api.commit
import com.jonaswanke.unicorn.api.git
import com.jonaswanke.unicorn.api.gitHub
import com.jonaswanke.unicorn.core.ProgramConfig
import com.jonaswanke.unicorn.core.ProjectConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.*
import net.swiftzer.semver.SemVer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHCreateRepositoryBuilder
import org.kohsuke.github.GHOrganization
import org.kohsuke.github.GHRepository
import org.kohsuke.github.HttpException
import java.io.File
import java.io.IOException
import java.util.*

open class Create : BaseCommand() {
    companion object {
        private const val GIT_GITIGNOREIO_ERROR_PREFIX = "#!! ERROR: "
        private const val GIT_GITIGNORE_FILE = ".gitignore"
        private const val GIT_GITATTRIBUTES_FILE = ".gitattributes"

        private const val CI_TRAVIS_CONFIG_FILE = ".travis-android.yml"
    }

    private val name by argument("name")
        .optional()
    private val description by option("-d", "--desc", "--description")
    private val type by option("-t", "--type")
        .choice(ProjectConfig.Type.stringToValueMap)
    private val version by option("-v", "--version")
        .convert { SemVer.parse(it) }

    val initInExisting get() = name == null
    override val prefix: File
        get() = super.prefix.let {
            if (initInExisting) it else File(it, name!!)
        }

    override fun execute(context: RunContext) {
        if (initInExisting)
            confirm("Using create in an existing project is experimental. Continue?", abort = true)

        val name = name
            ?: prefix.name

        if (initInExisting) {
            if (!prefix.exists())
                throw UsageError("The specified directory does not exist. If you want to create a new repository, please specify a name")
            if (!prefix.isDirectory)
                throw UsageError("The specified path is not a directory. If you want to create a new repository, please specify a name")
            try {
                context.projectConfig
                throw UsageError("Unicorn is already initialized in the specified directory")
            } catch (e: IOException) {
                // Means no project config file was found - expected
            }
        }

        val repo = context.gitHub.currentRepoOrNull(context)

        val description = description
            ?: if (initInExisting) repo?.description else null
                ?: promptOptional("Provide a short description")
        val type: ProjectConfig.Type = type
            ?: prompt<ProjectConfig.Type>(
                "What describes your project best? " +
                        "[${ProjectConfig.Type.stringToValueMap.keys.joinToString(", ")}]",
                default = "other"
            ) {
                val key = it.trim().toLowerCase()
                if (key.isBlank()) null
                else ProjectConfig.Type.stringToValueMap[key]
                    ?: throw NoSuchOption(key, ProjectConfig.Type.stringToValueMap.keys.toList())
            }
        val version = version
            ?: prompt<SemVer>(
                if (initInExisting) "What's the current version of your project?"
                else "What's the initial version of your project?",
                default = (if (initInExisting) repo?.latestRelease?.tagName?.removePrefix("v") else null)
                    ?: "0.0.1"
            ) {
                try {
                    SemVer.parse(it)
                } catch (e: IllegalArgumentException) {
                    throw BadParameterValue(it)
                }
            }

        val replacements = mapOf(
            "NAME" to name,
            "DESCRIPTION" to description,
            "TYPE" to type,
            "VERSION" to version,
            "YEAR" to Calendar.getInstance().get(Calendar.YEAR)
        ).mapKeys { (k, _) -> "%$k%" }
            .mapValues { (_, v) -> v.toString() }

        val webClient = OkHttpClient()


        // Files
        newLine()
        fun createFiles() {
            echo("Creating files...")

            if (!initInExisting) {
                echo("Creating directory")
                if (prefix.exists()) throw PrintMessage("The specified directory already exists!")
                prefix.mkdirs()
            }

            echo("Saving project config")
            val config = ProjectConfig(
                unicornVersion = ProgramConfig.VERSION,
                name = name,
                description = description,
                type = type,
                version = version
            )
            context.projectConfig = config

            echo("Copying templates")
            copyTemplate(replacements, "README.md")
            copyTemplate(replacements, "licenses/Apache License 2.0.txt", "LICENSE")
            File(".github").mkdir()
            copyTemplate(replacements, "github/PULL_REQUEST_TEMPLATE.md", ".github/PULL_REQUEST_TEMPLATE.md")
            File(".github/ISSUE_TEMPLATE").mkdir()
            copyTemplate(
                replacements,
                "github/ISSUE_TEMPLATE/1-bug-report.md",
                ".github/ISSUE_TEMPLATE/1-bug-report.md"
            )
            copyTemplate(
                replacements,
                "github/ISSUE_TEMPLATE/2-feature-request.md",
                ".github/ISSUE_TEMPLATE/2-feature-request.md"
            )
        }
        createFiles()


        // Travis CI
        newLine()
        if (!fileExists(CI_TRAVIS_CONFIG_FILE) && confirm("Setup Travis CI?", default = true) == true) {
            when (type) {
                ProjectConfig.Type.ANDROID -> {
                    echo("A config file is being generated for you, but you have to manually setup travis-ci.com to connect to GitHub and your repo.")
                    copyTemplate(replacements, "ci/travis-android.yml", CI_TRAVIS_CONFIG_FILE)
                }
                else -> echo("Unfortunately, no template is available for your configuration yet :(")
            }
        }


        // Git
        newLine()
        fun initGit(): Git {
            echo("Initializing git...")

            if (!fileExists(GIT_GITIGNORE_FILE)) {
                val gitignore = promptOptional(
                    "Please enter the .gitignore-template names from www.gitignore.io to use (separated by a comma)"
                ) { input ->
                    val templates = input?.split(",")
                        ?.map { it.trim().toLowerCase() }
                    if (templates.isNullOrEmpty()) return@promptOptional null

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
                }
                if (gitignore != null) {
                    copyTemplate(replacements, "gitignore", GIT_GITIGNORE_FILE)
                    File(prefix, GIT_GITIGNORE_FILE)
                        .appendText(gitignore)
                }
            }

            copyTemplate(replacements, "gitattributes", GIT_GITATTRIBUTES_FILE)

            if (isGitRepo(prefix)) return context.git

            return Git.init(prefix).apply {
                add(context, ".")
                commit(context, context.projectConfig.types.releaseCommit, description = "initial commit")

                checkout(context, flow.devBranch.name, createBranch = true)
            }
        }

        val git = initGit()


        // Github
        newLine()
        echo("Signing in to GitHub...")
        fun uploadToGithub(): GHRepository {
            echo("Connecting to GitHub...")

            echo("Creating repository")
            fun GHCreateRepositoryBuilder.init(private: Boolean): GHRepository {
                description(description)
                autoInit(false)
                private_(private)

                issues(true)
                wiki(false)

                allowMergeCommit(true)
                allowRebaseMerge(false)
                allowSquashMerge(false)
                return create()
            }

            val organization = promptOptional<GHOrganization>(
                "Which organization should this be uploaded to?",
                optionalText = " (Blank for no organization)"
            ) {
                if (it.isNullOrBlank()) null
                else try {
                    context.gitHub.api.getOrganization(it)
                } catch (e: IOException) {
                    throw NoSuchOption(it)
                }
            }

            val private = confirm("Should the repository be private?", default = true)
                ?: true
            val repoBuilder = organization?.createRepository(name)
                ?: context.gitHub.api.createRepository(name)
            val repo = try {
                repoBuilder.init(private)
            } catch (e: HttpException) {
                if (e.message?.contains("Visibility can't be private") == true) {
                    if (confirm("Your plan does not allow private repositories. Make it public instead?") != true)
                        throw CliktError("Cancelling...")
                    repoBuilder.init(false)
                } else throw e
            }

            echo("Uploading")
            git.addRemote(context, Constants.DEFAULT_REMOTE_NAME, URIish(repo.httpTransportUrl))
            git.trackBranch(context, git.flow.masterBranch.name)
            git.trackBranch(context, git.flow.devBranch.name)
            git.push(context, pushAllBranches = true, force = true)

            return repo
        }

        if (repo == null) {
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

                // Default branch
                echo("Setting ${git.flow.devBranch.name} as default branch")
                githubRepo.defaultBranch = git.flow.devBranch.name

                // Branch protection
                echo("Setting up branch protection")
                githubRepo.apply {
                    for (branch in listOf(getBranch(git.flow.masterBranch.name), getBranch(git.flow.devBranch.name)))
                        branch.enableProtection()
                            .requiredReviewers(1)
                            .includeAdmins(false)
                            .enable()
                }
            }
            configureGithub()
        }

        newLine()
    }

    data class Label(val name: String, val color: String)


    private fun fileExists(fileName: String): Boolean {
        return File(prefix, fileName).exists()
    }

    private fun isGitRepo(dir: File): Boolean {
        if (!dir.isDirectory) return false

        return RepositoryBuilder().apply {
            addCeilingDirectory(dir)
            findGitDir(dir)
        }.gitDir != null
    }

    // region Resources
    private fun copyTemplate(replacements: Replacements, resource: String, file: String = resource) {
        val dest = File(prefix, file)
        if (dest.exists()) return

        dest.outputStream().bufferedWriter().use { writer ->
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
