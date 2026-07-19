package com.interli.plural.core

import android.view.View
import android.view.ViewGroup

object SilentUi {
    fun disableSoundEffects(root: View?) {
        if (root == null) return
        if (root.isSoundEffectsEnabled) {
            root.isSoundEffectsEnabled = false
        }
        if (root.isHapticFeedbackEnabled) {
            root.isHapticFeedbackEnabled = false
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                disableSoundEffects(root.getChildAt(i))
            }
        }
    }
}
