package io.github.turtlepaw.mindsky.utils

fun String.sanitize(): String {
    return this
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
}
