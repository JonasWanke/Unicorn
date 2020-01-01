package com.jonaswanke.unicorn.core

import net.swiftzer.semver.SemVer
import java.io.File

object ProgramConfig {
    val VERSION = SemVer(0, 1, 0)

    val installationDir: File? = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile?.parentFile
}
