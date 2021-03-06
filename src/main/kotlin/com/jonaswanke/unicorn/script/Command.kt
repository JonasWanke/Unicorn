package com.jonaswanke.unicorn.script

import com.jonaswanke.unicorn.console.ConsoleRunContext
import com.jonaswanke.unicorn.core.BaseCommand
import com.jonaswanke.unicorn.script.parameters.UnicornParameter
import com.jonaswanke.unicorn.utils.MarkupBuilder
import com.jonaswanke.unicorn.utils.buildMarkup

class UnicornCommandBuilder(
    val name: String,
    val aliases: List<String>
) {
    // region Help
    @UnicornMarker
    var help: String = ""

    @UnicornMarker
    fun help(message: MarkupBuilder) {
        help = buildMarkup(message).toConsoleString()
    }
    // endregion

    // region Body
    var bodyBuilder: () -> BaseCommand = {
        object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = false) {}
    }

    @UnicornMarker
    fun run(body: Command0Body) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                override fun execute(context: ConsoleRunContext) = context.body()
            }
        }
    }

    @UnicornMarker
    fun <P1> run(param1: UnicornParameter<P1>, body: Command1Body<P1>) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                override fun execute(context: ConsoleRunContext) = context.body(p1)
            }
        }
    }

    @UnicornMarker
    fun <P1, P2> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        body: Command2Body<P1, P2>
    ) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                val p2: P2 by param2
                override fun execute(context: ConsoleRunContext) = context.body(p1, p2)
            }
        }
    }

    @UnicornMarker
    fun <P1, P2, P3> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        param3: UnicornParameter<P3>,
        body: Command3Body<P1, P2, P3>
    ) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                val p2: P2 by param2
                val p3: P3 by param3
                override fun execute(context: ConsoleRunContext) = context.body(p1, p2, p3)
            }
        }
    }

    @UnicornMarker
    fun <P1, P2, P3, P4> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        param3: UnicornParameter<P3>,
        param4: UnicornParameter<P4>,
        body: Command4Body<P1, P2, P3, P4>
    ) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                val p2: P2 by param2
                val p3: P3 by param3
                val p4: P4 by param4
                override fun execute(context: ConsoleRunContext) = context.body(p1, p2, p3, p4)
            }
        }
    }

    @UnicornMarker
    fun <P1, P2, P3, P4, P5> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        param3: UnicornParameter<P3>,
        param4: UnicornParameter<P4>,
        param5: UnicornParameter<P5>,
        body: Command5Body<P1, P2, P3, P4, P5>
    ) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                val p2: P2 by param2
                val p3: P3 by param3
                val p4: P4 by param4
                val p5: P5 by param5
                override fun execute(context: ConsoleRunContext) = context.body(p1, p2, p3, p4, p5)
            }
        }
    }

    @UnicornMarker
    fun <P1, P2, P3, P4, P5, P6> run(
        param1: UnicornParameter<P1>,
        param2: UnicornParameter<P2>,
        param3: UnicornParameter<P3>,
        param4: UnicornParameter<P4>,
        param5: UnicornParameter<P5>,
        param6: UnicornParameter<P6>,
        body: Command6Body<P1, P2, P3, P4, P5, P6>
    ) {
        bodyBuilder = {
            object : BaseCommand(name, aliases, help, invokeWithoutSubcommand = true) {
                val p1: P1 by param1
                val p2: P2 by param2
                val p3: P3 by param3
                val p4: P4 by param4
                val p5: P5 by param5
                val p6: P6 by param6
                override fun execute(context: ConsoleRunContext) = context.body(p1, p2, p3, p4, p5, p6)
            }
        }
    }
    // endregion

    // region Subcommands
    private val subcommands = mutableListOf<BaseCommand>()

    @UnicornMarker
    fun command(name: String, vararg aliases: String, commandBuilder: CommandBuilder) {
        val command = UnicornCommandBuilder(name, aliases.asList())
            .apply { commandBuilder() }
            .build()
        subcommands += command
    }
    // endregion

    fun build(): BaseCommand {
        return bodyBuilder().apply {
            subcommands.forEach { addSubcommand(it) }
        }
    }
}

typealias CommandBuilder = UnicornCommandBuilder.() -> Unit
typealias Command0Body = ConsoleRunContext.() -> Unit
typealias Command1Body<P1> = ConsoleRunContext.(P1) -> Unit
typealias Command2Body<P1, P2> = ConsoleRunContext.(P1, P2) -> Unit
typealias Command3Body<P1, P2, P3> = ConsoleRunContext.(P1, P2, P3) -> Unit
typealias Command4Body<P1, P2, P3, P4> = ConsoleRunContext.(P1, P2, P3, P4) -> Unit
typealias Command5Body<P1, P2, P3, P4, P5> = ConsoleRunContext.(P1, P2, P3, P4, P5) -> Unit
typealias Command6Body<P1, P2, P3, P4, P5, P6> = ConsoleRunContext.(P1, P2, P3, P4, P5, P6) -> Unit

@UnicornMarker
fun Unicorn.command(name: String, vararg aliases: String, commandBuilder: CommandBuilder) {
    val command = UnicornCommandBuilder(name, aliases.asList())
        .apply { commandBuilder() }
        .build()
    addCommand(command)
}
