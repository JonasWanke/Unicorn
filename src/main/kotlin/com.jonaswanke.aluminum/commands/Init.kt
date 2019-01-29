package com.jonaswanke.aluminum.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jonaswanke.aluminum.*
import com.jonaswanke.aluminum.utils.*
import net.swiftzer.semver.SemVer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHCreateRepositoryBuilder
import org.kohsuke.github.GHOrganization
import org.kohsuke.github.GHRepository
import org.kohsuke.github.HttpException
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
open class Create : BaseCommand() {
    companion object {
        private const val GIT_GITIGNOREIO_ERROR_PREFIX = "#!! ERROR: "
        private const val GIT_GITIGNORE_FILE = ".gitignore"
        private const val GIT_GITATTRIBUTES_FILE = ".gitattributes"

        private const val CI_TRAVIS_CONFIG_FILE = ".travis-android.yml"
    }

    private val name by argument("name")
    private val description by option("-d", "--desc", "--description")
    private val type by option("-t", "--type")
        .choice(ProjectConfig.Type.stringToValueMap)
    private val version by option("-v", "--version")
        .convert { SemVer.parse(it) }

    override fun run() {
        val description = description
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
            ?: prompt<SemVer>("What's the initial version of your project?", default = "0.0.1") {
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
        fun createFiles(): File {
            echo("Creating files...")

            echo("Creating directory")
            val dir = File("./$name")
            if (dir.exists()) throw PrintMessage("The specified directory already exists!")
            dir.mkdirs()

            echo("Saving project config")
            val config = ProjectConfig(
                aluminumVersion = ProgramConfig.VERSION,
                name = name,
                description = description,
                type = type,
                version = version
            )
            setProjectConfig(config, dir)

            echo("Copying templates")
            copyTemplate(dir, replacements, "README.md")
            copyTemplate(dir, replacements, "licenses/Apache License 2.0.txt", "LICENSE")
            return dir
        }

        val dir = createFiles()


        // Travis CI
        newLine()
        if (confirm("Setup Travis CI?", default = true) == true) {
            when (type) {
                ProjectConfig.Type.ANDROID -> {
                    echo("A config file is being generated for you, but you have to manually setup travis-ci.com to connect to GitHub and your repo.")
                    copyTemplate(dir, replacements, "ci/travis-android.yml", CI_TRAVIS_CONFIG_FILE)
                }
                else -> echo("Unfortunately, no template is available for your configuration yet :(")
            }
        }


        // Git
        newLine()
        fun initGit(): Git {
            echo("Initializing git...")

            val gitignore = promptOptional(
                "Please enter the .gitignore-template names from www.gitignore.io to use (separated by a comma)"
            ) { input ->
                val templates = input?.split(",")
                    ?.map { it.trim().toLowerCase() }
                    ?: emptyList()

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
            } ?: ""
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
        val (github, cp) = githubAuthenticate()
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
                    github.getOrganization(it)
                } catch (e: IOException) {
                    throw NoSuchOption(it)
                }
            }
            val private = confirm("Should the repository be private?", default = true)
                ?: true
            val repoBuilder = organization?.createRepository(name)
                ?: github.createRepository(name)
            val repo = try {
                repoBuilder.init(private)
            } catch (e: HttpException) {
                if (e.message?.contains("Visibility can't be private") == true) {
                    if (confirm("Your plan does not allow private repositories. Make it public instead?") != true)
                        throw CliktError("Cancelling...")
                    repoBuilder.init(false)
                } else throw e
            }
            setProjectConfig(getProjectConfig(dir).copy(githubName = repo.fullName), dir)

            echo("Uploading")
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
