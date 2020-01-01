copyDir(".github")


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
