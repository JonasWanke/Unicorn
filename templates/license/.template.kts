variables["company"] = prompt("Company (project owner/copyright holder)", default = global.gitHub?.username)

if (project.license !in listOf(null, License.NONE))
    copy("${project.license}.LICENSE.ftl", "LICENSE")
