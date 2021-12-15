package com.jonaswanke.unicorn.console

import com.jonaswanke.unicorn.core.readScript
import com.jonaswanke.unicorn.script.Unicorn


fun main(args: Array<String>) {
// Debug
//    if (System.console() == null)
//        System.setSecurityManager(object : SecurityManager() {
//            override fun checkExit(status: Int) {
//                throw SecurityException()
//            }
//
//            override fun checkPermission(perm: Permission?) = Unit
//        })

//    unicorn {
//                execute("./gradlew", "distZip")

//                    Files.Changelog.addRelease(version, name, )
//
//                    val release = gitHub.createRelease(version, tagName = "") {
//                        name = null
//                        date = Date()
//                        preRelease = false
//
//                        description = promptOptional("Enter a description")
//                        changelog = changelogFormatter {
//                            branch.newPrs
//                                .sort(...)
//                            .group(...)
//                            .joinToString("\n") {
//                            "- *${it.components.joinToString(", ")}:* ${it.name} (#${it.id})"
//                            + ", fixes ${it.fixedIssues.joinToString(", ") { "#${it.id}" }}"
//                        }
//                        }
//
//                        binaries += File("./build/distributions")  // Matcher
//                    }

//                    git.commit(ConventionalCommit.Type.CHORE, description = "bump version")
//
//                    val ghRelease = branch.release(release)
//
//                    Bintray.publishVersion(
//                        release = release,
//                        name = release.name,
//                        released = ghRelease.publishedAt,
//                        desc = release.description,
//                        githubReleaseNotesFile = Files.changelog.path,
//                        githubUseTagReleaseNotes = true,
//                        vcsTag = release.tagName
//                    ) {
//                        addFile(..., listInDownloads = true)
//                    }
//    }
//        Unicorn.main(listOf("release", "0.2.1", "--prefix=testRepo 1"))

//        Unicorn.main(listOf("login"))
//        Unicorn.main(listOf("create", "testRepo 1"))
//        Unicorn.main(listOf("-h"))
//        Unicorn.main(listOf("issue", "assign", "-h"))
//        Unicorn.main(listOf("issue", "assign", "1", "--prefix=testRepo 1"))
//        Unicorn.main(listOf("issue", "complete", "fix config", "--prefix=testRepo 1"))
//    } else {
    readScript()
    Unicorn.main(args.asList())
//    }
}

