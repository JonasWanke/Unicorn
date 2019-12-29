package com.jonaswanke.unicorn.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

val jsonConfig = JsonConfiguration.Stable.copy(
    strictMode = false,
    allowStructuredMapKeys = false
)
val json = Json(jsonConfig)
