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
    private var pendingToken: String? = null
    private var expiresAt: Long = Long.MAX_VALUE

    fun copy(label: String, value: String, clearAfterSeconds: Int = 30) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val token = java.util.UUID.randomUUID().toString()
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString("com.yishenghuang.keyic.CLIP_TOKEN", token)
        }
        clipboard.setPrimaryClip(clip)
        pendingToken = token
        clearJob?.cancel()
        expiresAt = if (clearAfterSeconds > 0) android.os.SystemClock.elapsedRealtime() + clearAfterSeconds * 1000L else Long.MAX_VALUE
        if (clearAfterSeconds > 0) clearJob = scope.launch {
            delay(clearAfterSeconds * 1000L)
            clearExpired()
        }
    }

    /** Android may deny background clipboard access; retry as soon as a Keyic activity resumes. */
    fun clearExpired() {
        if (android.os.SystemClock.elapsedRealtime() < expiresAt) return
        val expected = pendingToken ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            val description = clipboard.primaryClipDescription ?: return
            if (description.extras?.getString("com.yishenghuang.keyic.CLIP_TOKEN") == expected) {
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            pendingToken = null
        } catch (_: SecurityException) {
            // Keep the token (never the secret text) for the next foreground retry.
        }
    }
}
