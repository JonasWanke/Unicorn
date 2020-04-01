package com.jonaswanke.unicorn.console

import com.jonaswanke.unicorn.core.readScript
import com.jonaswanke.unicorn.script.Unicorn
import com.jonaswanke.unicorn.script.unicorn
import java.security.Permission

fun main(args: Array<String>) {
    readScript()
    Unicorn.main(args.asList())
}

