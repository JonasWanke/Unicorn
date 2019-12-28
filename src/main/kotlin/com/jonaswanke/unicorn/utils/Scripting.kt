package com.jonaswanke.unicorn.utils

object ScriptingUtils {
    fun buildCodeWithExtractedVariables(code: String, variables: Map<String, Any?>): String = buildString {
        variables.forEach {
            append("private val ${it.key} = ")
            val value = it.value

            if (value == null) append("null")
            else {
                append("bindings[\"${it.key}\"] as ")
                val clazz = value::class
                append(clazz.qualifiedName)
                if (clazz.typeParameters.isNotEmpty()) append(
                    clazz.typeParameters.joinToString(prefix = "<", postfix = ">") { "*" })
            }
            appendln(";")
        }
        append(code)
    }
}
