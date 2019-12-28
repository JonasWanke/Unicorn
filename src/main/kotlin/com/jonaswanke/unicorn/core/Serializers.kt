package com.jonaswanke.unicorn.core

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import net.swiftzer.semver.SemVer
import java.io.File

@Serializer(forClass = SemVer::class)
object SemVerSerializer : KSerializer<SemVer> {
    override val descriptor = StringDescriptor.withName("SemVer")

    override fun serialize(encoder: Encoder, obj: SemVer) = encoder.encodeString(obj.toString())
    override fun deserialize(decoder: Decoder): SemVer = SemVer.parse(decoder.decodeString())
}

@Serializer(forClass = File::class)
object FileSerializer : KSerializer<File> {
    override val descriptor = StringDescriptor.withName("File")

    override fun serialize(encoder: Encoder, obj: File) = encoder.encodeString(obj.path)
    override fun deserialize(decoder: Decoder): File = File(decoder.decodeString())
}
