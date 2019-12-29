package com.jonaswanke.unicorn.utils

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.File
import java.io.InputStream

private val yamlConfiguration = YamlConfiguration(strictMode = false)
private val yaml = Yaml(configuration = yamlConfiguration)


@UseExperimental(ImplicitReflectionSerializer::class)
internal inline fun <reified T : Any> File.readConfig(serializer: KSerializer<T> = T::class.serializer()): T =
    readText().readConfig(serializer)

@UseExperimental(ImplicitReflectionSerializer::class)
internal inline fun <reified T : Any> InputStream.readConfig(serializer: KSerializer<T> = T::class.serializer()): T =
    reader().use { it.readText() }.readConfig(serializer)

@UseExperimental(ImplicitReflectionSerializer::class)
internal inline fun <reified T : Any> String.readConfig(serializer: KSerializer<T> = T::class.serializer()): T =
    yaml.parse(serializer, this)


@UseExperimental(ImplicitReflectionSerializer::class)
internal inline fun <reified T : Any> File.writeConfig(value: T) =
    writeText(yaml.stringify(T::class.serializer(), value))
