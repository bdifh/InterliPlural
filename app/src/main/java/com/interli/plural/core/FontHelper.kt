package com.interli.plural.core

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object FontHelper {

    fun typeface(context: Context, style: Int = Typeface.NORMAL): Typeface {
        val family = context
            .getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .getString("font_family", "system")

        val baseTypeface = when (family) {
            "sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "serif" -> Typeface.create("serif", Typeface.NORMAL)
            "monospace" -> Typeface.create("monospace", Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }

        return Typeface.create(baseTypeface, style)
    }

    fun applyTo(view: View) {
        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.typeface = typeface(view.context, style)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTo(view.getChildAt(index))
            }
        }
    }
}