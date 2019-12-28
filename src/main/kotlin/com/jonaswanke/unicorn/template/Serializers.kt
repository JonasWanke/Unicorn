package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.utils.ScriptingUtils
import com.jonaswanke.unicorn.utils.decodeListElement
import com.jonaswanke.unicorn.utils.decodeNullableStringElement
import com.jonaswanke.unicorn.utils.encodeListElement
import kotlinx.serialization.*
import kotlinx.serialization.internal.LinkedHashMapSerializer
import kotlinx.serialization.internal.NamedMapClassDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl

@Serializer(forClass = List::class)
internal object ParameterListSerializer : KSerializer<List<TemplateParameter>> {
    override val descriptor = NamedMapClassDescriptor(
        "Parameter map",
        String.serializer().descriptor,
        TemplateParameter.serializer().descriptor
    )
    private val serializer = LinkedHashMapSerializer(String.serializer(), TemplateParameter.serializer())

    override fun serialize(encoder: Encoder, obj: List<TemplateParameter>) {
        val map = obj.map { it.id to it }.toMap()
        serializer.serialize(encoder, map)
    }

    override fun deserialize(decoder: Decoder): List<TemplateParameter> {
        return serializer.deserialize(decoder)
            .map { (id, param) -> param.withId(id = id) }
    }
}

@Serializer(forClass = TemplateParameter::class)
internal object TemplateParameterSerializer : KSerializer<TemplateParameter> {
    override val descriptor = object : SerialClassDescImpl("TemplateParameter") {
        init {
            addElement("type")
            addElement("name")
            addElement("help")
            addElement("required")
            addElement("default")
            addElement("validation")
            addElement("values")
        }
    }

    override fun serialize(encoder: Encoder, obj: TemplateParameter) {
        encoder.beginStructure(descriptor).let {
            it.encodeStringElement(
                descriptor, 0, when (obj) {
                    is TemplateParameter.StringParam -> TemplateParameter.StringParam.TYPE
                    is TemplateParameter.IntParam -> TemplateParameter.IntParam.TYPE
                    is TemplateParameter.EnumParam -> TemplateParameter.EnumParam.TYPE
                }
            )
            it.encodeStringElement(descriptor, 1, obj.name)
            obj.help?.let { h -> it.encodeStringElement(descriptor, 2, h) }
            it.encodeBooleanElement(descriptor, 3, obj.required)
            // Cannot encode default or validation as they're lambdas
            if (obj is TemplateParameter.EnumParam) it.encodeListElement(descriptor, 6, obj.values)
            it.endStructure(descriptor)
        }
    }

    @Suppress("RedundantExplicitType")
    override fun deserialize(decoder: Decoder): TemplateParameter {
        val dec = decoder.beginStructure(descriptor)
        var type: String = TemplateParameter.StringParam.TYPE
        var name: String = ""  // Will be replaced by ParameterListSerializer
        var help: String? = null
        var required: Boolean = true
        var default: String? = null
        var validation: String? = null
        var values: List<String>? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.READ_DONE -> break@loop
                0 -> type = dec.decodeStringElement(descriptor, i)
                1 -> name = dec.decodeStringElement(descriptor, i)
                2 -> help = dec.decodeNullableStringElement(descriptor, i)
                3 -> required = dec.decodeBooleanElement(descriptor, i)
                4 -> default = dec.decodeNullableStringElement(descriptor, i)
                5 -> validation = dec.decodeNullableStringElement(descriptor, i)
                6 -> values = dec.decodeListElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        dec.endStructure(descriptor)

        val defaultLambda: TemplateParameterDefaultFactory = {
            if (default == null) null
            else ScriptingUtils.evalInString(default, it)
        }
        val validationLambda: TemplateParameterValidation? =
            if (validation == null) null
            else { value, variables ->
                ScriptingUtils.eval(validation, variables + ("it" to value))
            }

        // ID will be replaced by ParameterListSerializer
        return when (type.toLowerCase()) {
            TemplateParameter.StringParam.TYPE ->
                TemplateParameter.StringParam("", name, help, required, defaultLambda, validationLambda)
            TemplateParameter.IntParam.TYPE ->
                TemplateParameter.IntParam("", name, help, required, defaultLambda, validationLambda)
            TemplateParameter.EnumParam.TYPE -> {
                values ?: throw MissingFieldException("type")
                require(values.isNotEmpty()) { "At least one value must be specified for enum parameter" }
                val emptyValues = values.withIndex()
                    .filter { (_, value) -> value.isBlank() }
                require(emptyValues.isEmpty()) { "Enum values must not be empty; was at index ${emptyValues.first().index + 1}" }

                TemplateParameter.EnumParam("", name, help, required, defaultLambda, validationLambda, values)
            }
            else -> error("Unknown parameter type \"$type\"")
        }
    }
}
