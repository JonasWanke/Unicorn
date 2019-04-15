package com.jonaswanke.unicorn.script.command

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.transformAll
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface UnicornArgumentDelegate<out T> : Argument, ReadOnlyProperty<Nothing?, T>

class UnicornProcessedArgument<AllT, ValueT> constructor(
    val command: UnicornCommand,
    val argument: ProcessedArgument<AllT, ValueT>
) : UnicornArgumentDelegate<AllT> {
    init {
        require(nvalues != 0) { "Arguments cannot have nvalues == 0" }
    }

    override val name: String get() = argument.name
    override val nvalues: Int get() = argument.nvalues
    override val required: Boolean get() = argument.required
    override val help: String get() = argument.help
    override val parameterHelp: HelpFormatter.ParameterHelp.Argument? get() = argument.parameterHelp


    override fun getValue(thisRef: Nothing?, property: KProperty<*>): AllT = argument.getValue(command, property)

    internal fun provideDelegate_() {
        argument.provideDelegate(command, ::NO_NAME)
    }

    override fun finalize(context: Context, values: List<String>) = argument.finalize(context, values)

    fun <AllT, ValueT> copy(
        argument: ProcessedArgument<AllT, ValueT>
    ): UnicornProcessedArgument<AllT, ValueT> {
        return UnicornProcessedArgument(command, argument)
    }
}

internal typealias UnicornRawArgument = UnicornProcessedArgument<String, String>

private const val NO_NAME: String = ""
fun UnicornCommand.argument(name: String, help: String = ""): UnicornRawArgument {
    val argument = (this as CliktCommand).argument(name, help)
    return UnicornProcessedArgument(this, argument)
}

fun <AllT, ValueT> UnicornProcessedArgument<AllT, ValueT>.register(): UnicornProcessedArgument<AllT, ValueT> {
    provideDelegate_()
    return this
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
): UnicornProcessedArgument<AllOutT, ValueT> {
    return copy(argument.transformAll(nvalues, required, transform))
}

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
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT, ValueT>.optional()
        : UnicornProcessedArgument<AllT?, ValueT> {
    return copy(argument.optional())
}

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
fun <T : Any> UnicornProcessedArgument<T, T>.multiple(required: Boolean = false): UnicornProcessedArgument<List<T>, T> {
    return copy(argument.multiple(required))
}

/**
 * Only store unique values for this argument
 *
 * Example:
 *
 * ```
 * val arg: Set<Int> by argument().int().multiple().unique()
 * ```
 */
fun <T : Any> UnicornProcessedArgument<List<T>, T>.unique(): UnicornProcessedArgument<Set<T>, T> {
    return copy(argument.unique())
}

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
fun <T : Any> UnicornProcessedArgument<T, T>.pair(): UnicornProcessedArgument<Pair<T, T>, T> {
    return copy(argument.pair())
}

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
fun <T : Any> UnicornProcessedArgument<T, T>.triple(): UnicornProcessedArgument<Triple<T, T, T>, T> {
    return copy(argument.triple())
}

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
fun <T : Any> UnicornProcessedArgument<T, T>.default(value: T): UnicornArgumentDelegate<T> {
    @Suppress("UNCHECKED_CAST")
    return copy(argument.default(value) as ProcessedArgument<T, T>)
}

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
inline fun <T : Any> UnicornProcessedArgument<T, T>.defaultLazy(crossinline value: () -> T): UnicornArgumentDelegate<T> {
    @Suppress("UNCHECKED_CAST")
    return copy(argument.defaultLazy(value) as ProcessedArgument<T, T>)
}

/**
 * Convert the argument's values.
 *
 * The [conversion] is called once for each value given. If any errors are thrown, they are caught and a
 * [BadParameterValue] is thrown with the error message. You can call `fail` to throw a [BadParameterValue]
 * manually.
 */
inline fun <T : Any> UnicornRawArgument.convert(crossinline conversion: ArgValueTransformer<T>)
        : UnicornProcessedArgument<T, T> {
    return copy(argument.convert(conversion))
}

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
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT, ValueT>.validate(validator: ArgValidator<AllT>)
        : UnicornArgumentDelegate<AllT> {
    @Suppress("UNCHECKED_CAST")
    return copy(argument.validate(validator) as ProcessedArgument<AllT, ValueT>)
}

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
fun <AllT : Any, ValueT> UnicornProcessedArgument<AllT?, ValueT>.validate(validator: ArgValidator<AllT>)
        : UnicornArgumentDelegate<AllT?> {
    @Suppress("UNCHECKED_CAST")
    return copy(argument.validate(validator) as ProcessedArgument<AllT?, ValueT>)
}
// endregion
