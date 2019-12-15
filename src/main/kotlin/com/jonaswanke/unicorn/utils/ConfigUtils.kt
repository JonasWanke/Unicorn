package com.jonaswanke.unicorn.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.swiftzer.semver.SemVer
import java.io.InputStream
import java.io.OutputStream

val mapper: ObjectMapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(
        KotlinModule(nullToEmptyCollection = true, nullToEmptyMap = true)
            .addSerializer(SemVer::class.java, object : StdSerializer<SemVer>(SemVer::class.java) {
                override fun serialize(value: SemVer?, gen: JsonGenerator?, provider: SerializerProvider?) {
                    gen?.writeString(value?.toString())
                }
            })
            .addDeserializer(SemVer::class.java, object : StdDeserializer<SemVer>(SemVer::class.java) {
                override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): SemVer {
                    val version = p!!.codec.readValue(p, String::class.java)
                    return SemVer.parse(version)
                }
            })
    )


inline fun <reified T> InputStream.readConfig(): T {
    return bufferedReader().use {
        mapper.readValue(this, T::class.java)
    }
}

fun <T> InputStream.readConfig(type: TypeReference<T>): T {
    return bufferedReader().use {
        mapper.readValue(this, type)
    }
}


fun <T> OutputStream.writeConfig(value: T) {
    return bufferedWriter().use {
        mapper.writeValue(this, value)
    }
}
