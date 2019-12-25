package com.jonaswanke.unicorn.script.parameters

import com.jonaswanke.unicorn.core.BaseCommand
import com.jonaswanke.unicorn.script.UnicornCommandBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The base interface of [UnicornArgument] and [UnicornOption] to allow mixing both when declaring commands using
 * [UnicornCommandBuilder.run].
 */
interface UnicornParameter<out T> {
    operator fun provideDelegate(thisRef: BaseCommand, prop: KProperty<*>): ReadOnlyProperty<BaseCommand, T>
}
