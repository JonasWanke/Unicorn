package com.jonaswanke.unicorn.script

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.commands.RunContext
import com.jonaswanke.unicorn.script.parameters.UnicornParameter
import com.jonaswanke.unicorn.utils.MarkupBuilder
import com.jonaswanke.unicorn.utils.buildMarkup

class UnicornCommandBuilder(
    val name: String,
    val aliases: List<String>
) {
    // region Help
    var help: String = ""

    fun help(message: MarkupBuilder) {
        help = buildMarkup(message).toConsoleString()
    }
    // endregion

    // region Body
    var bodyBuilder: BodyBuilder =
        NoBodyBuilder

    interface BodyBuilder {
        fun buildCommand(name: String, aliases: List<String>, help: String): BaseCommand
    }

    object NoBodyBuilder : BodyBuilder {
        override fun buildCommand(name: String, aliases: List<String>, help: String): BaseCommand {
            return object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = false) {

            }
        }
    }

    fun run(body: Command0Body) {
        bodyBuilder = Body0Builder(body)
    }

    class Body0Builder(val body: Command0Body) :
        BodyBuilder {
        override fun buildCommand(name: String, aliases: List<String>, help: String): BaseCommand =
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val a by option("a")
                    .convert { it.substring(1) }
                    .int()
                override fun execute(context: RunContext) = context.body()
            }
    }


    fun <P1> run(param1: UnicornParameter<P1>, body: Command1Body<P1>) {
        bodyBuilder = Body1Builder(param1, body)
    }

    class Body1Builder<P1>(
        val param1: UnicornParameter<P1>,
        val body: Command1Body<P1>
    ) : BodyBuilder {
        override fun buildCommand(name: String, aliases: List<String>, help: String): BaseCommand =
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1.build(this)
                override fun execute(context: RunContext) = context.body(p1)
            }
    }


    fun <P1, P2> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        body: Command2Body<P1, P2>
    ) {
        bodyBuilder = Body2Builder(param1, param2, body)
    }

    class Body2Builder<P1, P2>(
        val param1: UnicornParameter<P1>,
        val param2: UnicornParameter<P2>,
        val body: Command2Body<P1, P2>
    ) : BodyBuilder {
        override fun buildCommand(name: String, aliases: List<String>, help: String): BaseCommand =
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1.build(this)
                val p2: P2 by param2.build(this)
                override fun execute(context: RunContext) = context.body(p1, p2)
            }
    }
    // endregion

    fun build(): BaseCommand {
        return bodyBuilder.buildCommand(name, aliases, help)
    }
}

typealias CommandBuilder = UnicornCommandBuilder.() -> Unit
typealias Command0Body = RunContext.() -> Unit
typealias Command1Body<P1> = RunContext.(P1) -> Unit
typealias Command2Body<P1, P2> = RunContext.(P1, P2) -> Unit

@UnicornMarker
fun Unicorn.command(name: String, vararg aliases: String, commandBuilder: CommandBuilder) {
    val command = UnicornCommandBuilder(name, aliases.asList())
        .apply { commandBuilder() }
        .build()
    addCommand(command)
}

@UnicornMarker
fun BaseCommand.command(name: String, vararg aliases: String, commandBuilder: CommandBuilder) {
    val command = UnicornCommandBuilder(name, aliases.asList())
        .apply { commandBuilder() }
        .build()
    addSubcommand(command)
}
