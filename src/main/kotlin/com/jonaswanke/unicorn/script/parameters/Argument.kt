@file:Suppress("unused")

package com.jonaswanke.unicorn.script.parameters

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.*
import com.jonaswanke.unicorn.commands.BaseCommand
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface UnicornArgument<out T, A : ArgumentDelegate<T>> : UnicornParameter<T> {
    val name: String
    val help: String

    override fun provideDelegate(thisRef: BaseCommand, prop: KProperty<*>): ReadOnlyProperty<BaseCommand, T> {
        return build(thisRef).provideDelegate(thisRef, prop)
    }
    fun build(command: BaseCommand): A
}

internal typealias UnicornProcessedArgument<AllT, ValueT> = UnicornArgument<AllT, ProcessedArgument<AllT, ValueT>>
internal typealias UnicornRawArgument = UnicornProcessedArgument<String, String>


internal fun <InT, In : ArgumentDelegate<InT>, OutT, Out : ArgumentDelegate<OutT>> UnicornArgument<InT, In>.buildDelegate(
    transform: In.() -> Out
): UnicornArgument<OutT, Out> = object : UnicornArgument<OutT, Out> {
    override val name = this@buildDelegate.name
    override val help = this@buildDelegate.help

    override fun build(command: BaseCommand): Out = this@buildDelegate.build(command).transform()
}


fun argument(name: String, help: String = ""): UnicornRawArgument = object : UnicornRawArgument {
    override val name = name
    override val help = help

    override fun build(command: BaseCommand) = command.argument(name, help)
}

// region Transforms
/**
 * Transform all values to the final argument type.
 *
 * The input is a list of values, one for each value on the command line. The values in the
 * list are the output of calls to [convert]. The input list will have a size of [nvalues] if [nvalues] is > 0.
 *
 * Used to implement functions like [pair] and [multiple].
 *
 * @param nvalues The number of values required by this argument. A negative [nvalues] indicates a variable number
 *   of values.
 * @param required If true, an error with be thrown if no values are provided to this argument.
 */
fun <AllInT, ValueT, AllOutT> UnicornProcessedArgument<AllInT, ValueT>.transformAll(
    nvalues: Int? = null,
    required: Boolean? = null,
    transform: ArgCallsTransformer<AllOutT, ValueT>
) = buildDelegate { transformAll(nvalues, required, transform) }

/**
 * Return null instead of throwing an error if no value is given.
 *
 * This must be called after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val arg: Int? by argument().int().optional()
 * ```
 */
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT, ValueT>.optional() = buildDelegate { optional() }

/**
 * Accept any number of values to this argument.
 *
 * Only one argument in a command may use this function, and the command may not have subcommands. This must
 * be called after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val arg: List<Int> by argument().int().multiple()
 * ```
 */
fun <T : Any> UnicornProcessedArgument<T, T>.multiple(required: Boolean = false) = buildDelegate { multiple(required) }

/**
 * Only store unique values for this argument
 *
 * Example:
 *
 * ```
 * val arg: Set<Int> by argument().int().multiple().unique()
 * ```
 */
fun <T : Any> UnicornProcessedArgument<List<T>, T>.unique() = buildDelegate { unique() }

/**
 * Require exactly two values to this argument, and store them in a [Pair].
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val arg: Pair<Int, Int> by argument().int().pair()
 * ```
 */
fun <T : Any> UnicornProcessedArgument<T, T>.pair() = buildDelegate { pair() }

/**
 * Require exactly three values to this argument, and store them in a [Triple]
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val arg: Triple<Int, Int, Int> by argument().int().triple()
 * ```
 */
fun <T : Any> UnicornProcessedArgument<T, T>.triple() = buildDelegate { triple() }

/**
 * If the argument is not given, use [value] instead of throwing an error.
 *
 * This must be applied after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val arg: Pair<Int, Int> by argument().int().pair().default(1 to 2)
 * ```
 */
fun <T : Any> UnicornProcessedArgument<T, T>.default(value: T) = buildDelegate { default(value) }

/**
 * If the argument is not given, call [value] and use its return value instead of throwing an error.
 *
 * This must be applied after all other transforms. If the argument is given on the command line, [value] will
 * not be called.
 *
 * Example:
 *
 * ```kotlin
 * val arg: Pair<Int, Int> by argument().int().pair().defaultLazy { expensiveOperation() }
 * ```
 */
fun <T : Any> UnicornProcessedArgument<T, T>.defaultLazy(value: () -> T) = buildDelegate { defaultLazy(value) }

/**
 * Convert the argument's values.
 *
 * The [conversion] is called once for each value given. If any errors are thrown, they are caught and a
 * [BadParameterValue] is thrown with the error message. You can call `fail` to throw a [BadParameterValue]
 * manually.
 */
fun <T : Any> UnicornRawArgument.convert(conversion: ArgValueTransformer<T>) = buildDelegate { convert(conversion) }

/**
 * Check the final argument value and raise an error if it's not valid.
 *
 * The [validator] is called with the final argument type (the output of [transformAll]), and should call
 * `fail` if the value is not valid.
 *
 * You can also call `require` to fail automatically if an expression is false.
 *
 * Example:
 *
 * ```kotlin
 * val opt by argument().int().validate { require(it % 2 == 0) { "value must be even" } }
 * ```
 */
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT, ValueT>.validate(validator: ArgValidator<AllT>) =
    buildDelegate { validate(validator) }

/**
 * Check the final argument value and raise an error if it's not valid.
 *
 * The [validator] is called with the final argument type (the output of [transformAll]), and should call
 * `fail` if the value is not valid. It is not called if the delegate value is null.
 *
 * You can also call `require` to fail automatically if an expression is false.
 *
 * Example:
 *
 * ```kotlin
 * val opt by argument().int().validate { require(it % 2 == 0) { "value must be even" } }
 * ```
 */
@JvmName("nullableValidate")
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT?, ValueT>.validate(validator: ArgValidator<AllT>) =
    buildDelegate { validate(validator) }
// endregion

// region Types
/** Convert the argument values to an `Int` */
fun UnicornRawArgument.int(): UnicornProcessedArgument<Int, Int> = buildDelegate { int() }

/** Convert the argument values to an `Long` */
fun UnicornRawArgument.long(): UnicornProcessedArgument<Long, Long> = buildDelegate { long() }

/** Convert the argument values to an `Float` */
fun UnicornRawArgument.float(): UnicornProcessedArgument<Float, Float> = buildDelegate { float() }

/** Convert the argument values to an `Double` */
fun UnicornRawArgument.double(): UnicornProcessedArgument<Double, Double> = buildDelegate { double() }


/**
 * Restrict the argument values to fit into a range.
 *
 * By default, conversion fails if the value is outside the range, but if [clamp] is true, the value will be
 * silently clamped to fit in the range.
 *
 * Example:
 *
 * ```kotlin
 * argument().int().restrictTo(max=10, clamp=true)
 * ```
 */
fun <T> UnicornProcessedArgument<T, T>.restrictTo(min: T? = null, max: T? = null, clamp: Boolean = false)
        : UnicornProcessedArgument<T, T> where T : Number, T : Comparable<T> =
    buildDelegate { restrictTo(min, max, clamp) }

/**
 * Restrict the argument values to fit into a range.
 *
 * By default, conversion fails if the value is outside the range, but if [clamp] is true, the value will be
 * silently clamped to fit in the range.
 *
 * Example:
 *
 * ```kotlin
 * argument().int().restrictTo(1..10, clamp=true)
 * ```
 */
fun <T> UnicornProcessedArgument<T, T>.restrictTo(range: ClosedRange<T>, clamp: Boolean = false)
        where T : Number, T : Comparable<T> = buildDelegate { restrictTo(range, clamp) }

/**
 * Convert the argument based on a fixed set of values.
 *
 * Example:
 *
 * ```kotlin
 * argument().choice(mapOf("foo" to 1, "bar" to 2))
 * ```
 */
fun <T : Any> UnicornRawArgument.choice(choices: Map<String, T>): UnicornProcessedArgument<T, T> =
    buildDelegate { choice(choices) }

/**
 * Convert the argument based on a fixed set of values.
 *
 * Example:
 *
 * ```kotlin
 * argument().choice("foo" to 1, "bar" to 2)
 * ```
 */
fun <T : Any> UnicornRawArgument.choice(vararg choices: Pair<String, T>): UnicornProcessedArgument<T, T> =
    buildDelegate { choice(*choices) }

/**
 * Restrict the argument to a fixed set of values.
 *
 * Example:
 *
 * ```kotlin
 * argument().choice("foo", "bar")
 * ```
 */
fun UnicornRawArgument.choice(vararg choices: String): UnicornProcessedArgument<String, String> =
    buildDelegate { choice(*choices) }


/**
 * Convert the argument to a [File].
 *
 * @param exists If true, fail if the given path does not exist
 * @param fileOkay If false, fail if the given path is a file
 * @param folderOkay If false, fail if the given path is a directory
 * @param writable If true, fail if the given path is not writable
 * @param readable If true, fail if the given path is not readable
 */
fun UnicornRawArgument.file(
    exists: Boolean = false,
    fileOkay: Boolean = true,
    folderOkay: Boolean = true,
    writable: Boolean = false,
    readable: Boolean = false
): UnicornProcessedArgument<File, File> = buildDelegate { file(exists, fileOkay, folderOkay, writable, readable) }

/**
 * Convert the argument to a [Path].
 *
 * @param exists If true, fail if the given path does not exist
 * @param fileOkay If false, fail if the given path is a file
 * @param folderOkay If false, fail if the given path is a directory
 * @param writable If true, fail if the given path is not writable
 * @param readable If true, fail if the given path is not readable
 * @param fileSystem If specified, the [FileSystem] with which to resolve paths.
 */
fun UnicornRawArgument.path(
    exists: Boolean = false,
    fileOkay: Boolean = true,
    folderOkay: Boolean = true,
    writable: Boolean = false,
    readable: Boolean = false,
    fileSystem: FileSystem = FileSystems.getDefault()
): UnicornProcessedArgument<Path, Path> =
    buildDelegate { path(exists, fileOkay, folderOkay, writable, readable, fileSystem) }
// endregion
