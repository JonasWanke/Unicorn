package com.jonaswanke.unicorn.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.NoSuchOption
import com.jonaswanke.unicorn.api.*
import com.jonaswanke.unicorn.core.*
import com.jonaswanke.unicorn.core.ProjectConfig.License
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.command
import com.jonaswanke.unicorn.script.parameters.argument
import com.jonaswanke.unicorn.script.parameters.option
import com.jonaswanke.unicorn.script.parameters.optional
import com.jonaswanke.unicorn.template.Template
import com.jonaswanke.unicorn.utils.bold
import com.jonaswanke.unicorn.utils.italic
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.URIish
import org.kohsuke.github.GHOrganization
import org.kohsuke.github.GHRepository
import java.io.File
import java.io.IOException

fun Unicorn.registerCreateCommand() {
    command("create", "c") {
        help = "Create a new project"

        run(
            argument("name")
                .optional(),
            option("-d", "--desc", "--description"),
            option("-h", "--homepage")
                .url(),
            option("-l", "--license")
                .license(),
            option("-v", "--version")
                .semVer(),
            option("-t", "--template")
        ) { rawName, rawDescription, rawHomepage, rawLicense, rawVersion, rawTemplate ->
            val initInExisting = rawName == null
            if (initInExisting) confirm("Using create in an existing project is experimental. Continue?", abort = true)
            log.i {
                if (initInExisting) {
                    +"Initializing Unicorn in directory "
                    italic(projectDir.path)
                } else {
                    +"Creating new project "
                    bold(rawName)
                }
            }

            val name = rawName ?: projectDir.name
            if (!initInExisting)
                projectDir = File(projectDir, name!!)
            checkProjectDir(initInExisting, name)

            val repo = gitHub.currentRepoOrNull(this)

            val description = rawDescription
                ?: if (initInExisting) repo?.description else null
                    ?: promptOptional("Provide a short description")
            val homepage = rawHomepage
                ?: if (initInExisting) repo?.homepage?.parseUrlOrNull() else null
                    ?: promptOptionalUrl("What's the homepage of your project?")
            val license = rawLicense
                ?: promptOptional<License>("Choose a license") { keyword ->
                    License.fromKeywordOrNull(keyword)
                        ?: throw NoSuchOption(keyword, License.values().map { it.keyword })
                }
                ?: License.NONE
            val version = rawVersion
                ?: prompt<SemVer>(
                    if (initInExisting) "What's the current version of your project?"
                    else "What's the initial version of your project?",
                    default = repo?.latestReleaseVersion?.toString() ?: "0.0.1"
                ) {
                    try {
                        SemVer.parse(it)
                    } catch (e: IllegalArgumentException) {
                        throw BadParameterValue(it)
                    }
                }

            if (!initInExisting) {
                log.i("Creating directory")
                projectDir.mkdirs()
            }

            log.i("Saving project config")
            projectConfig = ProjectConfig(
                unicornVersion = ProgramConfig.VERSION,
                name = name,
                description = description,
                homepage = homepage,
                license = license,
                version = version
            )

            initGit()
            if (repo == null) initGitHub()

            applyTemplate(rawTemplate)

            upload()
        }
    }
}

private fun RunContext.checkProjectDir(initInExisting: Boolean, name: String) {
    if (initInExisting) {
        if (!projectDir.exists())
            exit("The specified directory does not exist. If you want to create a new repository, please specify a name")
        if (!projectDir.isDirectory)
            exit("The specified path is not a directory. If you want to create a new repository, please specify a name")

        try {
            projectConfig
            exit("Unicorn is already initialized in the specified directory")
        } catch (e: IOException) {
            // Means no project config file was found — expected
        }
    } else {
        if (projectDir.exists())
            exit {
                +"A subdirectory with the specified project name "
                italic(name)
                +" already exists!"
            }
    }
}

private const val GIT_GITIGNORE_FILE = ".gitignore"
private fun InteractiveRunContext.initGit() = group("Initializing git") {
    if (!File(projectDir, GIT_GITIGNORE_FILE).exists()) {
        val gitignore = promptOptional(
            "Please enter the .gitignore-template names from www.gitignore.io to use (comma-separated)"
        ) { input ->
            val templates = input.split(",")
                .map { it.trim().toLowerCase() }
            if (templates.isEmpty()) return@promptOptional null

            try {
                GitignoreIo.getTemplates(this, templates)
            } catch (e: GitignoreIo.UnknownTemplateException) {
                throw NoSuchOption(e.templateName)
            }
        }
        if (gitignore != null)
            File(projectDir, GIT_GITIGNORE_FILE).appendText(gitignore)
    }

    if (!Git.isInitializedIn(projectDir))
        Git.init(projectDir)
}

private fun InteractiveRunContext.initGitHub() = group("Configuring GitHub") {
    log.i("Creating repository")

    val organization = promptOptional<GHOrganization>(
        "Which organization should this be uploaded to?",
        optionalText = " (Empty for no organization)"
    ) {
        try {
            gitHub.api.getOrganization(it)
        } catch (e: IOException) {
            throw NoSuchOption(it)
        }
    }

    val private = confirm("Should the repository be private?", default = true)
    val repoConfig = GHRepositoryCreationConfig(
        name = projectConfig.name,
        description = projectConfig.description,
        homepage = projectConfig.homepage,
        private = private,
        issues = true,
        wiki = false,
        autoInit = false,
        allowMergeCommit = true,
        allowSquashMerge = false,
        allowRebaseMerge = false
    )

    fun create(private: Boolean): GHRepository {
        val config = repoConfig.copy(private = private)
        return try {
            organization?.createRepository(config) ?: gitHub.api.createRepository(config)
        } catch (e: GHRepoWithNameAlreadyExistsException) {
            exit("A repository with that name already exists. Aborting")
        }
    }

    val repo =
        if (!private) create(false)
        else try {
            create(true)
        } catch (e: GHRepoCantBePrivateException) {
            if (!confirm("Your plan does not allow private repositories. Make it public instead?"))
                exit("Aborting")
            create(false)
        }

    log.i("Deleting default labels")
    repo.listLabels().forEach { it.delete() }

    git.addRemote(this, Constants.DEFAULT_REMOTE_NAME, URIish(repo.httpTransportUrl))
    git.trackBranch(this, git.flow.masterBranch.name)
}


private fun InteractiveRunContext.applyTemplate(rawTemplate: String?) = group("Applying template") {
    val templateName = rawTemplate
        ?: prompt("Please choose a template", "base") {
            if (!Template.exists(this, it)) throw NoSuchOption(it, Template.getTemplateNames(this))
            it
        }
    Template.getByName(this, templateName).apply(this)
}


private fun RunContext.upload() = group("Commit and upload") {
    git.add(this, ".")
    // Doesn't make sense to look up the type in ProjectConfig as that was just created by us and couldn't be changed yet by the user
    git.commit(this, "chore", description = "initial commit")

    git.push(this, pushAllBranches = true, force = true)


    log.i("Setting ${git.flow.masterBranch.name} as default branch")
    gitHubRepo.defaultBranch = git.flow.masterBranch.name

    log.i("Configuring GitHub to delete branches on merge")
    gitHubRepo.deleteBranchOnMerge(true)

    log.i("Setting up branch protection")
    gitHubRepo.getBranch(git.flow.masterBranch.name)
        .enableProtection()
        .requiredReviewers(1)
        .includeAdmins(false)
        .enable()
}
