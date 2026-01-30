package io.github.pintowar.bellum.core.domain

import kotlin.reflect.KFunction

fun List<ValidationErrorDetail>.messagesAtPath(property: KFunction<*>): List<String> =
    filter { it.dataPath.trimStart('.') == property.name }.map { it.message }
