package com.jonaswanke.unicorn.core

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import net.swiftzer.semver.SemVer

@Serializer(forClass = SemVer::class)
object SemVerSerializer : KSerializer<SemVer> {
    override val descriptor = StringDescriptor.withName("SemVer")

    override fun serialize(encoder: Encoder, obj: SemVer) = encoder.encodeString(obj.toString())
    override fun deserialize(decoder: Decoder): SemVer = SemVer.parse(decoder.decodeString())
}
