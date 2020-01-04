// Templates
copyDir(".github/ISSUE_TEMPLATE")
copy(".github/PULL_REQUEST_TEMPLATE.md.ftl")


// Sponsors
val generateFunding = confirm("Generate FUNDING.yaml to display repo sponsor possibilities?", default = false)
if (generateFunding) {
    variables["gitHubSponsorsAccount"] = gitHubRepo.owner.login
    copy(".github/FUNDING.yml.ftl")
    log.i {
        bold("NOTE:")
        +" In order to display a \"Sponsor\"-button, you need to manually activate \"Sponsorships\" in " +
                "the repository settings."
    }
}


// Labels
val COLOR_GRAY = "cfd3d7"
val COLOR_PURPLE = "d876e3"
val COLOR_RED = "ff1744"

data class Label(
    val name: String,
    val description: String? = null,
    // https://youtrack.jetbrains.com/issue/KT-8199 keeps us from referencing the val as we're inside a function when
    // evaluating this script
    val color: String = "cfd3d7" // COLOR_GRAY
)

val labels = listOf(
    Label("duplicate"),
    Label("wontfix"),
    Label("discussion", color = COLOR_PURPLE),
    Label("question", color = COLOR_PURPLE),
    Label("PR: breaking", color = COLOR_RED)
)
labels.forEach {
    gitHubRepo.createLabel(it.name, it.color, it.description)
}

gitHubRepo.syncComponentLabels(this)
gitHubRepo.syncPriorityLabels(this)
gitHubRepo.syncTypeLabels(this)
