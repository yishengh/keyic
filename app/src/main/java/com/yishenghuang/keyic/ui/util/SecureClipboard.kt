package com.yishenghuang.keyic.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SecureClipboard(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var clearJob: Job? = null
    @Volatile
    private var pendingClearValue: String? = null

    fun copy(label: String, value: String, clearAfterSeconds: Int = 30) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        pendingClearValue = value
        clearJob?.cancel()
        if (clearAfterSeconds > 0) {
            clearJob = scope.launch {
                delay(clearAfterSeconds * 1000L)
                clearIfStillOurs(clipboard, value)
            }
        }
    }

    private fun clearIfStillOurs(clipboard: ClipboardManager, expected: String) {
        try {
            val current = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            if (current != expected) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } finally {
            if (pendingClearValue == expected) pendingClearValue = null
        }
    }
}
