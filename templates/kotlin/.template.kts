applyTemplate("base")

copy("gradle.properties")
copy("gradlew")
copy("gradlew.bat")
copyDir("gradle/wrapper")

val packageName = prompt("Package name")
variables["packageName"] = packageName

val isApplication = confirm("Is this an application? (No for library)", default = true)
if (isApplication) variables["isApplication"] = true
else variables["isLibrary"] = true

val usingIntelliJ = confirm("Are you using IntelliJ IDEA?", default = true)
if (usingIntelliJ) variables["usingIntelliJ"] = true

copy("build.gradle.ftl")
copy("src/main/kotlin/Main.kt.ftl", "src/main/kotlin/${packageName.replace('.', '/')}/Main.kt")

val gitignoreTemplates = listOfNotNull(
    "kotlin",
    "gradle",
    if (usingIntelliJ) "intellij" else null
)
write(".gitignore", GitignoreIo.getTemplates(this, gitignoreTemplates), FileWriteMode.APPEND)
