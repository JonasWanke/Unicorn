package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import net.swiftzer.semver.SemVer
import java.io.File

object Flutter {
    val flutterHome: String
        get() = System.getenv("FLUTTER_HOME")

    enum class BuildType(val cliFlag: String) {
        DEBUG("--debug"),
        PROFILE("--profile"),
        RELEASE("--release")
    }

    @Suppress("LongParameterList")
    fun buildAppBundle(
        context: RunContext,
        directory: File? = context.projectDir,
        buildType: BuildType = BuildType.RELEASE,
        target: String = "lib\\main.dart",
        flavor: String? = null,
        runPubGet: Boolean = true,
        buildNumber: Int? = null,
        buildName: SemVer? = null
    ) {
        val arguments = listOfNotNull(
            "build",
            "appbundle",
            buildType.cliFlag,
            "--target=$target",
            if (flavor != null) "--flavor=$flavor" else null,
            if (runPubGet) "--pub" else "--no-pub",
            if (buildNumber != null) "--build-number=$buildNumber" else null,
            if (buildName != null) "--build-name=$buildName" else null
        ).toTypedArray()

        @Suppress("SpreadOperator")
        context.execute("$flutterHome/bin/flutter", *arguments, directory = directory)
    }
}
