package com.jonaswanke.unicorn.utils

import kotlinx.serialization.*
import kotlinx.serialization.internal.ArrayListSerializer
import kotlinx.serialization.internal.nullable

fun CompositeDecoder.decodeNullableStringElement(desc: SerialDescriptor, index: Int): String? =
    decodeNullableSerializableElement(desc, index, String.serializer().nullable)

// region List
fun CompositeEncoder.encodeListElement(
    desc: SerialDescriptor,
    index: Int,
    value: List<String>
) = encodeListElement(desc, index, String.serializer(), value)

fun CompositeDecoder.decodeListElement(
    desc: SerialDescriptor,
    index: Int
): List<String> = decodeListElement(desc, index, String.serializer())


fun <T> CompositeEncoder.encodeListElement(
    desc: SerialDescriptor,
    index: Int,
    elementSerializer: KSerializer<T>,
    value: List<T>
) = encodeSerializableElement(desc, index, ArrayListSerializer(elementSerializer), value)

fun <T> CompositeDecoder.decodeListElement(
    desc: SerialDescriptor,
    index: Int,
    elementSerializer: KSerializer<T>
): List<T> = decodeSerializableElement(desc, index, ArrayListSerializer(elementSerializer))
// endregion
