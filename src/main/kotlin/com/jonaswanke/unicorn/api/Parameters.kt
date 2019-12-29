package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.ProjectConfig
import com.jonaswanke.unicorn.script.parameters.*
import com.jonaswanke.unicorn.template.Template
import net.swiftzer.semver.SemVer
import org.jetbrains.kotlin.utils.keysToMap

fun UnicornRawArgument.semVer(): UnicornProcessedArgument<SemVer, SemVer> =
    convert { SemVer.parse(it.removePrefix("v")) }

fun UnicornRawOption.semVer(): UnicornNullableOption<SemVer, SemVer> = convert { SemVer.parse(it.removePrefix("v")) }


fun UnicornRawArgument.license(): UnicornProcessedArgument<ProjectConfig.License, ProjectConfig.License> =
    choice(ProjectConfig.License.values().map { it.keyword to it }.toMap())

fun UnicornRawOption.license(): UnicornNullableOption<ProjectConfig.License, ProjectConfig.License> =
    choice(ProjectConfig.License.values().map { it.keyword to it }.toMap())


fun UnicornRawArgument.template(): UnicornProcessedArgument<String, String> =
    choice(Template.getAllTemplateNames().keysToMap { it })

fun UnicornRawOption.template(): UnicornNullableOption<String, String> =
    choice(Template.getAllTemplateNames().keysToMap { it })
