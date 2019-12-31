applyTemplate("base")

fun TemplateRunContext.generatePackage(
    name: String = projectConfig.name,
    isLibrary: Boolean = true,
    isCodeGenerator: Boolean = false
) {
    if (!Dart.isPackageNameValid(name))
        log.w("Package name \"$name\" is invalid. Use only basic Latin letters and Arabic digits: [a-z0-9_]")

    variables["project"] = projectConfig.copy(name = name)
    copy("pubspec.yaml.ftl")
    copy("analysis_options.yaml")
    copy(".gitignore.ftl")

    if (isLibrary) copy("lib/package.dart.ftl", "lib/$name.dart")
    else copy("lib/main.dart")
    copyDir("lib/src")

    if (isLibrary) {
        if (isCodeGenerator) copyDir("generator-example", "example")
        else copyDir("example")
    }
    copyDir("test")

    Dart.Pub.get(this, directory = baseDir)
}


variables["homepage"] = promptOptionalUrl("Homepage (for pubspec.yaml; visible on pub.dev)")
variables["minSdkVersion"] = promptSemVer("Minimum Dart SDK version", default = "2.5.0")

if (confirm("Is this an application package? (No for library packages)", default = true)) {
    // Single application package
    generatePackage(isLibrary = false)
} else {
    variables["isLibrary"] = true
    if (!confirm("Multiple packages in this project?", default = false)) {
        // Single library package
        generatePackage()
    } else {
        // Multiple library packages
        do {
            val name = prompt("Please enter a package name")
            val hasGenerator = confirm("Shall a corresponding code generator package be generated?", default = false)
            inSubdir(name) {
                generatePackage(name)
            }

            if (hasGenerator) {
                val codeGenName = "${name}_generator"
                inSubdir(codeGenName) {
                    variables["isGenerator"] = true
                    variables["basePackage"] = name
                    generatePackage(codeGenName, isCodeGenerator = true)
                }
            }
        } while (confirm("Add another package?", default = false))
    }
}
