package com.jonaswanke.unicorn.utils

import com.jonaswanke.unicorn.core.scriptEngine
import javax.script.SimpleBindings

object ScriptingUtils {
    private val imports = listOf(
        "com.jonaswanke.unicorn.api.*",
        "com.jonaswanke.unicorn.core.*",
        "com.jonaswanke.unicorn.core.ProjectConfig.*",
        "net.swiftzer.semver.SemVer"
    )
    private fun buildCodeWithExtractedVariables(code: String, variables: Map<String, Any?>): String = buildString {
        imports.forEach {
            appendln("import $it;")
        }

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

    fun <T> eval(code: String, variables: Map<String, Any?>): T {
        val fullCode = buildCodeWithExtractedVariables(code, variables)
        val value = scriptEngine.eval(fullCode, SimpleBindings(variables.mapValues { it.value }))
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun evalInString(interpolation: String, variables: Map<String, Any?>): String? {
        return eval<String?>("\"$interpolation\"", variables)
            .takeUnless { it == "null" }
    }
}
