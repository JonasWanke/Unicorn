@file:Suppress("unused")

package com.jonaswanke.unicorn.script.parameters

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import com.jonaswanke.unicorn.commands.BaseCommand
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

interface UnicornOption<out T, A : OptionDelegate<T>> : UnicornParameter<T> {
    val names: List<String>
    val help: String
    val metavar: String?
    val hidden: Boolean
    val envvar: String?

    override fun build(command: BaseCommand): A
}

internal typealias UnicornOptionWithValues<AllT, EachT, ValueT> = UnicornOption<AllT, OptionWithValues<AllT, EachT, ValueT>>
internal typealias UnicornNullableOption<EachT, ValueT> = UnicornOption<EachT?, OptionWithValues<EachT?, EachT, ValueT>>
internal typealias UnicornRawOption = UnicornNullableOption<String, String>


internal fun <InT, In : OptionDelegate<InT>, OutT, Out : OptionDelegate<OutT>> UnicornOption<InT, In>.buildDelegate(
    transform: In.() -> Out
): UnicornOption<OutT, Out> = object : UnicornOption<OutT, Out> {
    override val names = this@buildDelegate.names
    override val help = this@buildDelegate.help
    override val metavar = this@buildDelegate.metavar
    override val hidden = this@buildDelegate.hidden
    override val envvar = this@buildDelegate.envvar

    override fun build(command: BaseCommand): Out = this@buildDelegate.build(command).transform()
}


fun option(
    vararg names: String,
    help: String = "",
    metavar: String? = null,
    hidden: Boolean = false,
    envvar: String? = null
): UnicornRawOption = object : UnicornRawOption {
    override val names = names.asList()
    override val help = help
    override val metavar = metavar
    override val hidden = hidden
    override val envvar = envvar

    override fun build(command: BaseCommand) = command.option(*names, help)
}

// region Transforms
/**
 * Transform all calls to the option to the final option type.
 *
 * The input is a list of calls, one for each time the option appears on the command line. The values in the
 * list are the output of calls to [transformValues]. If the option does not appear from any source (command
 * line or envvar), this will be called with an empty list.
 *
 * Used to implement functions like [default] and [multiple].
 */
fun <AllT, EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.transformAll(transform: CallsTransformer<EachT, AllT>) =
    buildDelegate { transformAll(transform) }

/**
 * If the option is not called on the command line (and is not set in an envvar), use [value] for the option.
 *
 * This must be applied after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val opt: Pair<Int, Int> by option().int().pair().default(1 to 2)
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.default(value: EachT) = buildDelegate { default(value) }

/**
 * If the option is not called on the command line (and is not set in an envvar), call the [value] and use its
 * return value for the option.
 *
 * This must be applied after all other transforms. If the option is given on the command line, [value] will
 * not be called.
 *
 * Example:
 *
 * ```kotlin
 * val opt: Pair<Int, Int> by option().int().pair().defaultLazy { expensiveOperation() }
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.defaultLazy(value: () -> EachT) =
    buildDelegate { defaultLazy(value) }

/**
 * If the option is not called on the command line (and is not set in an envvar), throw a [MissingParameter].
 *
 * This must be applied after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val opt: Pair<Int, Int> by option().int().pair().required()
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.required() = buildDelegate { required() }

/**
 * Make the option return a list of calls; each item in the list is the value of one call.
 *
 * If the option is never called, the list will be empty. This must be applied after all other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val opt: List<Pair<Int, Int>> by option().int().pair().multiple()
 * ```
 *
 * @param default The value to use if the option is not supplied. Defaults to an empty list.
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.multiple(default: List<EachT> = emptyList()) =
    buildDelegate { multiple(default) }

/**
 * Make the [multiple] option return a unique set of calls
 *
 * Example:
 *
 * ```kotlin
 * val opt: Set<Int> by option().int().multiple().unique()
 * ```
 */
fun <EachT : Any, ValueT> UnicornOptionWithValues<List<EachT>, EachT, ValueT>.unique() = buildDelegate { unique() }


/**
 * Change the number of values that this option takes.
 *
 * The input will be a list of size [nvalues], with each item in the list being the output of a call to
 * [convert]. [nvalues] must be 2 or greater, since options cannot take a variable number of values, and
 * [option] has [nvalues] = 1 by default. If you want to change the type of an option with one value, use
 * [convert] instead.
 *
 * Used to implement functions like [pair] and [triple]. This must be applied before any other transforms.
 */
fun <EachInT : Any, EachOutT : Any, ValueT> UnicornNullableOption<EachInT, ValueT>.transformValues(
    nvalues: Int,
    transform: ArgsTransformer<ValueT, EachOutT>
) = buildDelegate { transformValues(nvalues, transform) }

/**
 * Change to option to take two values, held in a [Pair]
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val opt: Pair<Int, Int>? by option().int().pair()
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.pair() = buildDelegate { pair() }

/**
 * Change to option to take three values, held in a [Triple]
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * Example:
 *
 * ```kotlin
 * val opt: Triple<Int, Int, Int>? by option().int().triple()
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.triple() = buildDelegate { triple() }


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
fun <AllT : Any, EachT, ValueT> UnicornOptionWithValues<AllT, EachT, ValueT>.validate(validator: OptionValidator<AllT>) =
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
fun <AllT : Any, EachT, ValueT> UnicornOptionWithValues<AllT?, EachT, ValueT>.validate(validator: OptionValidator<AllT>) =
    buildDelegate { validate(validator) }


/**
 * Convert the option value type.
 *
 * The [conversion] is called once for each value in each invocation of the option. If any errors are thrown,
 * they are caught and a [BadParameterValue] is thrown with the error message. You can call `fail` to throw a
 * [BadParameterValue] manually.
 *
 * @param metavar The metavar for the type. Overridden by a metavar passed to [option].
 * @param envvarSplit If the value is read from an envvar, the pattern to split the value on. The default
 *   splits on whitespace.
 */
fun <T : Any> UnicornRawOption.convert(
    metavar: String = "VALUE",
    envvarSplit: Regex = Regex("\\s+"),
    conversion: ValueTransformer<T>
) = buildDelegate { convert(metavar, envvarSplit, conversion) }
// endregion

// region Types
/** Convert the argument values to an `Int` */
fun UnicornRawOption.int(): UnicornNullableOption<Int, Int> = buildDelegate { int() }

/** Convert the argument values to an `Long` */
fun UnicornRawOption.long(): UnicornNullableOption<Long, Long> = buildDelegate { long() }

/** Convert the argument values to an `Float` */
fun UnicornRawOption.float(): UnicornNullableOption<Float, Float> = buildDelegate { float() }

/** Convert the argument values to an `Double` */
fun UnicornRawOption.double(): UnicornNullableOption<Double, Double> = buildDelegate { double() }


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
fun <T> UnicornNullableOption<T, T>.restrictTo(min: T? = null, max: T? = null, clamp: Boolean = false)
        : UnicornNullableOption<T, T> where T : Number, T : Comparable<T> =
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
fun <T> UnicornNullableOption<T, T>.restrictTo(range: ClosedRange<T>, clamp: Boolean = false)
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
fun <T : Any> UnicornRawOption.choice(choices: Map<String, T>): UnicornNullableOption<T, T> =
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
fun <T : Any> UnicornRawOption.choice(vararg choices: Pair<String, T>): UnicornNullableOption<T, T> =
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
fun UnicornRawOption.choice(vararg choices: String): UnicornNullableOption<String, String> =
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
fun UnicornRawOption.file(
    exists: Boolean = false,
    fileOkay: Boolean = true,
    folderOkay: Boolean = true,
    writable: Boolean = false,
    readable: Boolean = false
): UnicornNullableOption<File, File> = buildDelegate { file(exists, fileOkay, folderOkay, writable, readable) }

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
fun UnicornRawOption.path(
    exists: Boolean = false,
    fileOkay: Boolean = true,
    folderOkay: Boolean = true,
    writable: Boolean = false,
    readable: Boolean = false,
    fileSystem: FileSystem = FileSystems.getDefault()
): UnicornNullableOption<Path, Path> =
    buildDelegate { path(exists, fileOkay, folderOkay, writable, readable, fileSystem) }
// endregion
