package com.interli.plural.core

import android.content.Context
import android.text.style.BackgroundColorSpan
import io.noties.markwon.SpanFactory
import io.noties.markwon.simple.ext.SimpleExtPlugin

object MarkdownHighlight {
    fun create(context: Context) =
        SimpleExtPlugin.create { plugin ->
            val highlightColor = ColorHelper.getBtnColor(context)

            plugin.addExtension(
                2,
                '=',
                SpanFactory { _, _ ->
                    BackgroundColorSpan(highlightColor)
                }
            )
        }
}