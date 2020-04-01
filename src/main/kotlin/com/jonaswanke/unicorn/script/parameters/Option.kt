@file:Suppress("unused", "TooManyFunctions")

package com.jonaswanke.unicorn.script.parameters

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import com.jonaswanke.unicorn.core.BaseCommand
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface UnicornOption<T, A : OptionDelegate<T>> : UnicornParameter<T> {
    val names: List<String>
    val help: String
    val metavar: String?
    val hidden: Boolean
    val envvar: String?

    override fun provideDelegate(thisRef: BaseCommand, prop: KProperty<*>): ReadOnlyProperty<BaseCommand, T> {
        return build(thisRef).provideDelegate(thisRef, prop)
    }

    fun build(command: BaseCommand): A
}

internal typealias UnicornOptionWithValues<AllT, EachT, ValueT>
        = UnicornOption<AllT, OptionWithValues<AllT, EachT, ValueT>>

internal typealias UnicornNullableOption<EachT, ValueT> = UnicornOption<EachT?, OptionWithValues<EachT?, EachT, ValueT>>
internal typealias UnicornRawOption = UnicornNullableOption<String, String>
internal typealias UnicornFlagOption<T> = UnicornOption<T, FlagOption<T>>


fun <InT, In : OptionDelegate<InT>, OutT, Out : OptionDelegate<OutT>> UnicornOption<InT, In>.buildDelegate(
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

    @Suppress("SpreadOperator")
    override fun build(command: BaseCommand) = command.option(*names, help = help)
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
fun <AllT, EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.transformAll(
    transform: CallsTransformer<EachT, AllT>
) = buildDelegate { transformAll(transform = transform) }

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
 * You can customize how the default is shown to the user with [defaultForHelp]. The default value
 * is an empty string, so if you have the help formatter configured to show values, you should set
 * this value manually.
 *
 * ### Example:
 *
 * ```kotlin
 * val opt: Pair<Int, Int> by option().int().pair().defaultLazy { expensiveOperation() }
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.defaultLazy(
    defaultForHelp: String = "",
    value: () -> EachT
) = buildDelegate { defaultLazy(defaultForHelp, value) }

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
 * ### Example:
 *
 * ```kotlin
 * val opt: List<Pair<Int, Int>> by option().int().pair().multiple()
 * ```
 *
 * @param default The value to use if the option is not supplied. Defaults to an empty list.
 * @param required If true, [default] is ignored and [MissingParameter] will be thrown if no
 *   instances of the option are present on the command line.
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.multiple(
    default: List<EachT> = emptyList(),
    required: Boolean = false
) = buildDelegate { multiple(default, required) }

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
 * Change to option to take any number of values, separated by a [regex].
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * ### Example:
 *
 * ```kotlin
 * val opt: List<Int>? by option().int().split(Regex(","))
 * ```
 *
 * Which can be called like this:
 *
 * ```
 * ./program --opt 1,2,3
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.split(regex: Regex) = buildDelegate { split(regex) }

/**
 * Change to option to take any number of values, separated by a string [delimiter].
 *
 * This must be called after converting the value type, and before other transforms.
 *
 * ### Example:
 *
 * ```kotlin
 * val opt: List<Int>? by option().int().split(Regex(","))
 * ```
 *
 * Which can be called like this:
 *
 * ```
 * ./program --opt 1,2,3
 * ```
 */
fun <EachT : Any, ValueT> UnicornNullableOption<EachT, ValueT>.split(delimiter: String) =
    buildDelegate { split(delimiter) }

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
fun <AllT : Any, EachT, ValueT> UnicornOptionWithValues<AllT, EachT, ValueT>.validate(
    validator: OptionValidator<AllT>
) = buildDelegate { validate(validator) }

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
fun <AllT : Any, EachT, ValueT> UnicornOptionWithValues<AllT?, EachT, ValueT>.validate(
    validator: OptionValidator<AllT>
) = buildDelegate { validate(validator) }


/**
 * Mark this option as deprecated in the help output.
 *
 * By default, a tag is added to the help message and a warning is printed if the option is used.
 *
 * This should be called after any conversion and validation.
 *
 * ### Example:
 *
 * ```kotlin
 * val opt by option().int().validate { require(it % 2 == 0) { "value must be even" } }
 *    .deprecated("WARNING: --opt is deprecated, use --new-opt instead")
 * ```
 *
 * @param message The message to show in the warning or error. If null, no warning is issued.
 * @param tagName The tag to add to the help message
 * @param tagValue An extra message to add to the tag
 * @param error If true, when the option is invoked, a [CliktError] is raised immediately instead of issuing a warning.
 */
fun <AllT, EachT, ValueT> UnicornOptionWithValues<AllT, EachT, ValueT>.deprecated(
    message: String? = "",
    tagName: String? = "deprecated",
    tagValue: String = "",
    error: Boolean = false
) = buildDelegate { deprecated(message, tagName, tagValue, error) }

/**
 * Convert the option value type.
 *
 * The [conversion] is called once for each value in each invocation of the option. If any errors are thrown,
 * they are caught and a [BadParameterValue] is thrown with the error message. You can call `fail` to throw a
 * [BadParameterValue] manually.
 *
 * @param metavar The metavar for the type. Overridden by a metavar passed to [option].
 * @param envvarSplit If the value is read from an envvar, the pattern to split the value on. The default
 *   splits on whitespace. This value is can be overridden by passing a value to the [option] function.
 * @param completionCandidates candidates to use when completing this option in shell autocomplete
 */
fun <T : Any> UnicornRawOption.convert(
    metavar: String = "VALUE",
    envvarSplit: Regex = Regex("\\s+"),
    completionCandidates: CompletionCandidates? = null,
    conversion: ValueTransformer<T>
) = buildDelegate {
    if (completionCandidates != null) convert(metavar, envvarSplit, completionCandidates, conversion)
    else convert(metavar, envvarSplit, conversion = conversion)
}

/**
 * If the option isn't given on the command line, prompt the user for manual input.
 *
 * @param text The text to prompt the user with
 * @param default The default value to use if no input is given. If null, the prompt will be repeated until
 *   input is given.
 * @param hideInput If true, user input will not be shown on the screen. Useful for passwords and sensitive
 *   input.
 * @param requireConfirmation If true, the user will be required to enter the same value twice before it is
 *   accepted.
 * @param confirmationPrompt If [requireConfirmation] is true, this will be used to ask for input again.
 * @param promptSuffix Text to display directly after [text]. Defaults to ": ".
 * @param showDefault Show [default] to the user in the prompt.
 */
fun <T : Any> UnicornNullableOption<T, T>.prompt(
    text: String? = null,
    default: String? = null,
    hideInput: Boolean = false,
    requireConfirmation: Boolean = false,
    confirmationPrompt: String = "Repeat for confirmation: ",
    promptSuffix: String = ": ",
    showDefault: Boolean = true
) = buildDelegate {
    prompt(text, default, hideInput, requireConfirmation, confirmationPrompt, promptSuffix, showDefault)
}
// endregion

// region Flag
/**
 * Turn an option into a boolean flag.
 *
 * @param secondaryNames additional names for that option that cause the option value to be false. It's good
 *   practice to provide secondary names so that users can disable an option that was previously enabled.
 * @param default the value for this property if the option is not given on the command line.
 */
@Suppress("SpreadOperator")
fun UnicornRawOption.flag(vararg secondaryNames: String, default: Boolean = false): UnicornFlagOption<Boolean> =
    buildDelegate { flag(*secondaryNames, default = default) }

/**
 * Turn an option into a flag that counts the number of times the option occurs on the command line.
 */
fun UnicornRawOption.counted(): UnicornFlagOption<Int> = buildDelegate { counted() }

/** Turn an option into a set of flags that each map to a value. */
fun <T : Any> UnicornRawOption.switch(choices: Map<String, T>): UnicornFlagOption<T?> =
    buildDelegate { switch(choices) }

/** Turn an option into a set of flags that each map to a value. */
@Suppress("SpreadOperator")
fun <T : Any> UnicornRawOption.switch(vararg choices: Pair<String, T>): UnicornFlagOption<T?> =
    buildDelegate { switch(*choices) }

/** Set a default value for a option. */
@JvmName("flagDefault")
fun <T : Any> UnicornFlagOption<T?>.default(value: T): UnicornFlagOption<T> = buildDelegate { default(value) }

/**
 * Check the final option value and raise an error if it's not valid.
 *
 * The [validator] is called with the final option type (the output of [transformAll]), and should call `fail`
 * if the value is not valid. It is not called if the delegate value is null.
 */
@JvmName("flagValidate")
fun <T : Any> UnicornFlagOption<T>.validate(validator: OptionValidator<T>): UnicornOption<T, OptionDelegate<T>> =
    buildDelegate { validate(validator) }
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


private fun mvar(choices: Iterable<String>): String {
    return choices.joinToString("|", prefix = "[", postfix = "]")
}

/**
 * Convert the option based on a fixed set of values.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice(mapOf("foo" to 1, "bar" to 2))
 * ```
 *
 * @see com.github.ajalt.clikt.parameters.groups.groupChoice
 */
fun <T : Any> UnicornRawOption.choice(
    choices: Map<String, T>,
    metavar: String = mvar(choices.keys)
): UnicornNullableOption<T, T> = buildDelegate { choice(choices, metavar) }

/**
 * Convert the option based on a fixed set of values.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice("foo" to 1, "bar" to 2)
 * ```
 *
 * @see com.github.ajalt.clikt.parameters.groups.groupChoice
 */
@Suppress("SpreadOperator")
fun <T : Any> UnicornRawOption.choice(
    vararg choices: Pair<String, T>,
    metavar: String = mvar(choices.map { it.first })
): UnicornNullableOption<T, T> = buildDelegate { choice(*choices, metavar = metavar) }

/**
 * Restrict the option to a fixed set of values.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice("foo", "bar")
 * ```
 */
@Suppress("SpreadOperator")
fun UnicornRawOption.choice(
    vararg choices: String,
    metavar: String = mvar(choices.asIterable())
): UnicornNullableOption<String, String> = buildDelegate { choice(*choices, metavar = metavar) }

/**
 * Convert the option to the values of an enum.
 *
 * ### Example:
 *
 * ```kotlin
 * enum class Size { SMALL, LARGE }
 * option().enum<Size>()
 * ```
 *
 * @param key A block that returns the command line value to use for an enum value. The default is
 *   the enum name.
 */
inline fun <reified T : Enum<T>> UnicornRawOption.enum(
    crossinline key: (T) -> String = { it.name }
): UnicornNullableOption<T, T> =
    buildDelegate { enum(key) }


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
