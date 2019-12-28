package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.FileSerializer
import com.jonaswanke.unicorn.core.ProgramConfig
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.core.group
import com.jonaswanke.unicorn.utils.*
import kotlinx.serialization.*
import kotlinx.serialization.internal.LinkedHashMapSerializer
import kotlinx.serialization.internal.NamedMapClassDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
import java.io.File

class Template private constructor(
    val name: String,
    val dir: File,
    val config: TemplateConfig
) {
    companion object {
        const val TEMPLATES_DIR_NAME = "templates"
        const val CONFIG_NAME = ".template.yml"

        val templatesDir = File(ProgramConfig.installationDir, TEMPLATES_DIR_NAME)
        fun getAllTemplateNames(): List<String> = templatesDir.listFiles()!!.map { it.name }

        fun getByName(context: RunContext, name: String): Template = context.group("Parsing template $name") {
            val dir = File(templatesDir, name)
            if (!dir.exists()) exit {
                +"Template not found — directory "
                italic(dir.absolutePath)
                +" doesn't exist"
            }

            val config = try {
                File(dir, CONFIG_NAME).readConfig<TemplateConfig>()
            } catch (e: IllegalArgumentException) {
                exit(e.message!!)
            } catch (e: IllegalStateException) {
                exit(e.message!!)
            } catch (e: MissingFieldException) {
                exit(e.message!!)
            }

            Template(name, dir, config)
        }
    }
}

@Serializable
data class TemplateConfig(
    val description: String? = null,
    @Serializable(ParameterListSerializer::class)
    val parameters: List<TemplateParameter> = emptyList(),
    val dependsOn: List<TemplateReference> = emptyList(),
    val files: List<FileExpansion> = emptyList()
) {
    @Serializer(forClass = List::class)
    object ParameterListSerializer : KSerializer<List<TemplateParameter>> {
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

    @Serializable
    data class TemplateReference(
        val name: String,
        @Serializable(FileSerializer::class)
        val baseDir: File = File("."),
        val parameters: Map<String, String> = emptyMap()
    ) {
        fun evalParameters(variables: TemplateVariables): TemplateVariables {
            return parameters.mapValues { (_, value) -> ScriptingUtils.evalInString(value, variables) }
        }
    }

    @Serializable
    data class FileExpansion(
        val from: String,
        val to: String? = null,
        val isTemplate: Boolean = true,
        val condition: String? = null
    ) {
        fun evalFrom(variables: TemplateVariables): String {
            return ScriptingUtils.evalInString(from, variables)
        }

        fun evalCondition(variables: TemplateVariables): Boolean {
            return condition == null || ScriptingUtils.eval(condition, variables)
        }
    }
}

typealias TemplateParamDefaultGetter = (variables: TemplateVariables) -> String?
typealias TemplateParamValidation = (value: Any?, variables: TemplateVariables) -> Boolean

@Serializable(with = TemplateParameter.Serializer::class)
sealed class TemplateParameter {
    abstract val id: String
    abstract val name: String
    abstract val help: String?
    abstract val required: Boolean
    abstract val default: TemplateParamDefaultGetter
    abstract val validation: TemplateParamValidation?

    abstract fun withId(id: String): TemplateParameter


    data class StringParam(
        override val id: String,
        override val name: String,
        override val help: String? = null,
        override val required: Boolean = true,
        override val default: TemplateParamDefaultGetter = { null },
        override val validation: TemplateParamValidation? = null
    ) : TemplateParameter() {
        companion object {
            const val TYPE = "string"
        }

        override fun withId(id: String) = copy(id = id, name = name.takeUnless { it.isBlank() } ?: id)
    }

    data class IntParam(
        override val id: String,
        override val name: String,
        override val help: String? = null,
        override val required: Boolean = true,
        override val default: TemplateParamDefaultGetter = { null },
        override val validation: TemplateParamValidation? = null
    ) : TemplateParameter() {
        companion object {
            const val TYPE = "int"
        }

        override fun withId(id: String) = copy(id = id, name = name.takeUnless { it.isBlank() } ?: id)
    }

    data class EnumParam(
        override val id: String,
        override val name: String,
        override val help: String? = null,
        override val required: Boolean = true,
        override val default: TemplateParamDefaultGetter = { null },
        override val validation: TemplateParamValidation? = null,
        val values: List<String>
    ) : TemplateParameter() {
        companion object {
            const val TYPE = "enum"
        }

        override fun withId(id: String) = copy(id = id, name = name.takeUnless { it.isBlank() } ?: id)
    }

    @kotlinx.serialization.Serializer(forClass = TemplateParameter::class)
    object Serializer : KSerializer<TemplateParameter> {
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
                        is StringParam -> StringParam.TYPE
                        is IntParam -> IntParam.TYPE
                        is EnumParam -> EnumParam.TYPE
                    }
                )
                it.encodeStringElement(descriptor, 1, obj.name)
                obj.help?.let { h -> it.encodeStringElement(descriptor, 2, h) }
                it.encodeBooleanElement(descriptor, 3, obj.required)
                // Cannot encode default or validation as they're lambdas
                if (obj is EnumParam) it.encodeListElement(descriptor, 6, obj.values)
                it.endStructure(descriptor)
            }
        }

        @Suppress("RedundantExplicitType")
        override fun deserialize(decoder: Decoder): TemplateParameter {
            val dec = decoder.beginStructure(descriptor)
            var type: String? = null
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

            type ?: throw MissingFieldException("type")

            val defaultLambda: TemplateParamDefaultGetter = {
                if (default == null) null
                else ScriptingUtils.evalInString(default, it)
            }
            val validationLambda: TemplateParamValidation? =
                if (validation == null) null
                else { value, variables ->
                    ScriptingUtils.eval(validation, variables + ("it" to value))
                }

            return when (type.toLowerCase()) {
                StringParam.TYPE -> StringParam("", name, help, required, defaultLambda, validationLambda)
                IntParam.TYPE -> IntParam("", name, help, required, defaultLambda, validationLambda)
                EnumParam.TYPE -> {
                    values ?: throw MissingFieldException("type")
                    require(values.isNotEmpty()) { "At least one value must be specified for enum parameter" }
                    val emptyValues = values.withIndex()
                        .filter { (_, value) -> value.isBlank() }
                    require(emptyValues.isEmpty()) { "Enum values must not be empty; was at index ${emptyValues.first().index + 1}" }

                    EnumParam("", name, help, required, defaultLambda, validationLambda, values)
                }
                else -> error("Unknown parameter type \"$type\"")
            }
        }
    }
}
