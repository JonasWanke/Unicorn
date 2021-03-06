package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.ProjectConfig
import com.jonaswanke.unicorn.script.parameters.*

fun UnicornRawArgument.license(): UnicornProcessedArgument<ProjectConfig.License, ProjectConfig.License> =
    choice(ProjectConfig.License.values().map { it.keyword to it }.toMap())

fun UnicornRawOption.license(): UnicornNullableOption<ProjectConfig.License, ProjectConfig.License> =
    choice(ProjectConfig.License.values().map { it.keyword to it }.toMap())
