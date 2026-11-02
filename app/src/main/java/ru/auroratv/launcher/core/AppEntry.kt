package ru.auroratv.launcher.core

import android.content.ComponentName

data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val hasBanner: Boolean,
    val system: Boolean
) {
    val key: String get() = component.flattenToString()
}
