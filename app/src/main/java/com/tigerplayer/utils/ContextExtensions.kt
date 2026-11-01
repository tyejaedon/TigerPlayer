package com.tigerplayer.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Traverses the [Context] hierarchy unwrapping [ContextWrapper]s until finding
 * the hosting [Activity], or null if none exists in this context chain.
 */
tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
