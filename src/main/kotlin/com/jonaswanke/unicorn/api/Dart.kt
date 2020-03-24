@file:Suppress("unused")

package com.jonaswanke.unicorn.api

import com.jonaswanke.unicorn.core.RunContext
import com.jonaswanke.unicorn.utils.code
import java.io.File

object Dart {
    val PACKAGE_NAME_PATTERN = "^[a-z0-9_]+$".toPattern()

    fun isPackageNameValid(name: String): Boolean = PACKAGE_NAME_PATTERN.matcher(name).matches()

    object Pub {
        fun get(context: RunContext, offline: Boolean = false, directory: File? = context.projectDir) {
            val arguments = listOfNotNull(
                "get",
                if (offline) "--offline" else null
            ).toTypedArray()
            @Suppress("SpreadOperator")
            run(context, *arguments, directory = directory)
        }

        fun run(context: RunContext, vararg arguments: String, directory: File? = context.projectDir) {
            context.log.i {
                code("pub ${arguments.joinToString(" ")}")
            }
            @Suppress("SpreadOperator")
            context.execute("pub", *arguments, directory = directory)
        }
    }
}
