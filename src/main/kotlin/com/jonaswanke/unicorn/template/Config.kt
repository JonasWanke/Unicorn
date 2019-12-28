package com.jonaswanke.unicorn.template

import com.jonaswanke.unicorn.core.FileSerializer
import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.ScriptingUtils
import com.jonaswanke.unicorn.utils.italic
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TemplateConfig(
    val description: String? = null,
    @Serializable(ParameterListSerializer::class)
    val parameters: List<TemplateParameter> = emptyList(),
    val dependsOn: List<TemplateReference> = emptyList(),
    val files: List<FileExpansion> = emptyList()
) {
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
        fun evalFrom(context: RunContext, variables: TemplateVariables): String {
            return ScriptingUtils.evalInString(from, variables)
                ?: context.exit {
                    +"A file expansion's "
                    italic("from")
                    +" evaluated to "
                    italic("null")
                    +" (Code: \"$from\")"
                }
        }

        fun evalCondition(variables: TemplateVariables): Boolean {
            return condition == null || ScriptingUtils.eval(condition, variables)
        }
    }
}

typealias TemplateParameterDefaultFactory = (variables: TemplateVariables) -> String?
typealias TemplateParameterValidation = (value: Any?, variables: TemplateVariables) -> Boolean

@Serializable(with = TemplateParameterSerializer::class)
sealed class TemplateParameter {
    abstract val id: String
    abstract val name: String
    abstract val help: String?
    abstract val required: Boolean
    abstract val default: TemplateParameterDefaultFactory
    abstract val validation: TemplateParameterValidation?

    abstract fun withId(id: String): TemplateParameter


    data class StringParam(
        override val id: String,
        override val name: String,
        override val help: String? = null,
        override val required: Boolean = true,
        override val default: TemplateParameterDefaultFactory = { null },
        override val validation: TemplateParameterValidation? = null
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
        override val default: TemplateParameterDefaultFactory = { null },
        override val validation: TemplateParameterValidation? = null
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
        override val default: TemplateParameterDefaultFactory = { null },
        override val validation: TemplateParameterValidation? = null,
        val values: List<String>
    ) : TemplateParameter() {
        companion object {
            const val TYPE = "enum"
        }

        override fun withId(id: String) = copy(id = id, name = name.takeUnless { it.isBlank() } ?: id)
    }
}
