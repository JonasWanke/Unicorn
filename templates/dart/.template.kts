applyTemplate("base")

fun TemplateRunContext.generate(name: String, isCodeGenerator: Boolean = false) {
    variables["project"] = projectConfig.copy(name = name)
    copy("pubspec.yaml.ftl")
    copy("analysis_options.yaml")
    copy(".gitignore.ftl")
    copy("lib/package.dart.ftl", "lib/$name.dart")
    copyDir("lib/src")
    if (!isCodeGenerator) copyDir("example")
    copyDir("test")
}


variables["homepage"] = promptOptionalUrl("Homepage (for pubspec.yaml; visible on pub.dev)")
variables["minSdkVersion"] = promptSemVer("Minimum Dart SDK version", default = "2.5.0")

if (!confirm("Multiple packages in this project?", default = false)) {
    generate(projectConfig.name)
} else {
    do {
        val name = prompt("Please enter a package name")
        val hasGenerator = confirm("Shall a corresponding code generator package be generated?", default = false)
        inSubdir("name") {
            generate(name)
        }

        if (hasGenerator) {
            val codeGenName = "${name}_generator"
            inSubdir(codeGenName) {
                variables["isGenerator"] = true
                variables["basePackage"] = name
                generate(codeGenName, isCodeGenerator = true)
            }
        }
    } while (confirm("Add another package?", default = false))
}
