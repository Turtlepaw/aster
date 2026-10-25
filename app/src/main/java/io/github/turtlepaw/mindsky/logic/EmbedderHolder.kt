package io.github.turtlepaw.mindsky.logic

import android.content.Context

object EmbedderHolder {
    @Volatile private var instance: PostEmbedder? = null

    fun get(context: Context): PostEmbedder =
        instance ?: synchronized(this) {
            instance ?: PostEmbedder(context.applicationContext).also { instance = it }
        }
}