package com.jonaswanke.aluminum.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.InputStream

inline fun <reified T> InputStream.readConfig(): T {
    return bufferedReader().use {
        ObjectMapper(YAMLFactory()).apply {
            registerModule(KotlinModule())
        }.readValue(this, T::class.java)
    }
}

fun <T> InputStream.readConfig(type: TypeReference<T>): T {
    return bufferedReader().use {
        ObjectMapper(YAMLFactory()).apply {
            registerModule(KotlinModule())
        }.readValue(this, type)
    }
}
