package com.jonaswanke.unicorn.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

fun <T : Any> cached(initialGetter: () -> T, setter: (T) -> Unit): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        lateinit var value: T

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!::value.isInitialized) value = initialGetter()
            return value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
            setter(value)
        }
    }
}

operator fun <R> KProperty0<R>.getValue(thisRef: Any, property: KProperty<*>): R = get()
operator fun <R> KMutableProperty0<R>.setValue(thisRef: Any, property: KProperty<*>, value: R) = set(value)
