package com.jonaswanke.unicorn.utils

import kotlin.properties.ReadOnlyProperty
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
            lazy(initialGetter)
        }
    }
}

operator fun <R> KProperty0<R>.getValue(thisRef: Any, property: KProperty<*>): R = get()
operator fun <R> KMutableProperty0<R>.setValue(thisRef: Any, property: KProperty<*>, value: R) = set(value)

fun <R, T> lazy(initializer: R.() -> T): ReadOnlyProperty<R, T> = SynchronizedLazyImpl(initializer)

private object UninitializedValue
// Mostly copied from Kotlin's JVM lazy
private class SynchronizedLazyImpl<in R, out T>(initializer: R.() -> T) : ReadOnlyProperty<R, T> {
    private var initializer: (R.() -> T)? = initializer
    @Volatile
    private var _value: Any? = UninitializedValue

    override fun getValue(thisRef: R, property: KProperty<*>): T {
        val v1 = _value
        if (v1 !== UninitializedValue) {
            @Suppress("UNCHECKED_CAST")
            return v1 as T
        }

        return synchronized(this) {
            val v2 = _value
            if (v2 !== UninitializedValue) {
                @Suppress("UNCHECKED_CAST") (v2 as T)
            } else {
                val typedValue = initializer!!(thisRef)
                _value = typedValue
                initializer = null
                typedValue
            }
        }
    }

    val isInitialized: Boolean
        get() = _value !== UninitializedValue

    override fun toString(): String = if (isInitialized) _value.toString() else "Lazy value not initialized yet."
}
