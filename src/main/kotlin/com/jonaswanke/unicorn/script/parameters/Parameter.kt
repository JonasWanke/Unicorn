package com.jonaswanke.unicorn.script.parameters

import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.script.UnicornCommandBuilder
import kotlin.properties.ReadOnlyProperty

/**
 * The base interface of [UnicornArgument] and [UnicornOption] to allow mixing both when declaring commands using
 * [UnicornCommandBuilder.run].
 */
interface UnicornParameter<out T> {
    fun build(command: BaseCommand): ReadOnlyProperty<BaseCommand, T>
}
