package com.jonaswanke.unicorn.utils

import com.jonaswanke.unicorn.core.scriptEngine
import javax.script.SimpleBindings

object ScriptingUtils {
    private val imports = listOf(
        "com.jonaswanke.unicorn.api.*",
        "com.jonaswanke.unicorn.core.*",
        "com.jonaswanke.unicorn.core.ProjectConfig.*",
        "com.jonaswanke.unicorn.template.*",
        "net.swiftzer.semver.SemVer"
    )
    const val RECEIVER_BINDING_NAME = "__receiver"

    private fun buildCodeWithExtractedVariables(code: String, receiver: Any, variables: Map<String, Any?>): String =
        buildString {
            fun appendBinding(key: String, value: Any?) {
                if (value == null) append("null")
                else {
                    append("bindings[\"${key}\"] as ")
                    val clazz = value::class
                    append(clazz.qualifiedName)
                    if (clazz.typeParameters.isNotEmpty()) append(
                        clazz.typeParameters.joinToString(prefix = "<", postfix = ">") { "*" })
                }
            }

            imports.forEach {
                appendln("import $it;")
            }

            variables.forEach {
                append("private val ${it.key} = ")
                appendBinding(it.key, it.value)
                appendln(";")
            }

            append("with(")
            appendBinding(RECEIVER_BINDING_NAME, receiver)
            append("){")
            append(code)
            append("}")
        }

    fun <T> eval(code: String, receiver: Any, variables: Map<String, Any?>): T {
        val fullCode = buildCodeWithExtractedVariables(code, receiver, variables)
        val bindings = SimpleBindings(variables.mapValues { it.value } + (RECEIVER_BINDING_NAME to receiver))

        val value = scriptEngine.eval(fullCode, bindings)
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
