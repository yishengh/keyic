package com.yishenghuang.keyic.ui.util

import android.content.Context
import com.yishenghuang.keyic.R

/**
 * User-facing message with optional action label for Snackbars / dialogs.
 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val isError: Boolean = false,
)

object UserError {
    fun vaultLocked(context: Context): UiMessage =
        UiMessage(context.getString(R.string.error_vault_locked), isError = true)

    fun biometricFailed(context: Context): UiMessage =
        UiMessage(context.getString(R.string.error_biometric_failed), isError = true)

    fun safPermission(context: Context): UiMessage =
        UiMessage(context.getString(R.string.error_saf_permission), isError = true)

    fun backupFailed(context: Context, detail: String? = null): UiMessage =
        UiMessage(
            text = detail?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.error_backup_failed_detail, it)
            } ?: context.getString(R.string.error_backup_failed),
            isError = true,
        )

    fun importFailed(context: Context, detail: String? = null): UiMessage =
        UiMessage(
            text = detail?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.error_import_failed),
            isError = true,
        )

    fun generic(context: Context, detail: String?): UiMessage =
        UiMessage(
            detail?.ifBlank { null } ?: context.getString(R.string.error_generic),
            isError = true,
        )

    fun movedToRecycleBin(context: Context, onUndo: (() -> Unit)? = null): UiMessage =
        UiMessage(
            text = context.getString(R.string.moved_to_recycle_bin),
            actionLabel = if (onUndo != null) context.getString(R.string.action_undo) else null,
            action = onUndo,
        )
}
