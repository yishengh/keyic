package com.yishenghuang.keyic.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KeyicAutofillService : AutofillService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val parsed = AutofillStructureParser.parse(structure)
        if (!parsed.hasAnyFillable) {
            callback.onSuccess(null)
            return
        }

        val container = (application as KeyicApp).container
        scope.launch {
            try {
                val unlocked = container.session.isUnlocked.first()
                val builder = FillResponse.Builder()
                attachSaveInfo(builder, parsed)

                if (!unlocked) {
                    builder.addDataset(lockedDataset(parsed))
                    callback.onSuccess(builder.build())
                    return@launch
                }

                val entries = container.vaultRepository.entries.first()
                val matched = container.autofillMatcher.match(
                    packageName = parsed.packageName,
                    webDomain = parsed.webDomain,
                    entries = entries,
                    preferCards = parsed.hasCardFields && !parsed.hasLoginFields,
                )
                matched.forEach { entry ->
                    builder.addDataset(datasetForEntry(entry, parsed))
                }
                val vaultCount = container.vaultRegistry.vaults.first().size
                if (matched.isEmpty() && vaultCount > 1) {
                    builder.addDataset(switchVaultDataset(parsed))
                }
                callback.onSuccess(builder.build())
            } catch (_: Exception) {
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val extracted = structure?.let { AutofillStructureParser.extractCredentials(it) }
        if (extracted == null || (extracted.username.isBlank() && extracted.password.isBlank())) {
            callback.onSuccess()
            return
        }
        val intent = Intent(this, AutofillSaveActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SAVE_USERNAME, extracted.username)
            putExtra(EXTRA_SAVE_PASSWORD, extracted.password)
            putExtra(EXTRA_PACKAGE, extracted.packageName)
            putExtra(EXTRA_DOMAIN, extracted.webDomain)
        }
        startActivity(intent)
        callback.onSuccess()
    }

    private fun attachSaveInfo(builder: FillResponse.Builder, parsed: ParsedAutofillFields) {
        val ids = listOfNotNull(
            parsed.usernameId,
            parsed.passwordId,
            parsed.cardNumberId,
            parsed.cardExpiryId,
            parsed.cardCvvId,
            parsed.cardHolderId,
        ).toTypedArray()
        if (ids.isEmpty()) return
        var type = 0
        if (parsed.usernameId != null) type = type or SaveInfo.SAVE_DATA_TYPE_USERNAME
        if (parsed.passwordId != null) type = type or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        if (parsed.hasCardFields) type = type or SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD
        builder.setSaveInfo(
            SaveInfo.Builder(type, ids)
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .build(),
        )
    }

    private fun lockedDataset(parsed: ParsedAutofillFields): Dataset {
        val intent = Intent(this, AutofillUnlockActivity::class.java).apply {
            putParsedExtras(parsed)
        }
        val pending = PendingIntent.getActivity(
            this,
            REQ_UNLOCK,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or mutable(),
        )
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, getString(R.string.autofill_unlock))
        }
        return Dataset.Builder(presentation)
            .setAuthentication(pending.intentSender)
            .apply { applyNullValues(parsed) }
            .build()
    }

    private fun switchVaultDataset(parsed: ParsedAutofillFields): Dataset {
        val intent = Intent(this, AutofillVaultPickActivity::class.java).apply {
            putParsedExtras(parsed)
        }
        val pending = PendingIntent.getActivity(
            this,
            REQ_SWITCH_VAULT,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or mutable(),
        )
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, getString(R.string.autofill_other_vault))
        }
        return Dataset.Builder(presentation)
            .setAuthentication(pending.intentSender)
            .apply { applyNullValues(parsed) }
            .build()
    }

    private fun datasetForEntry(entry: VaultEntry, parsed: ParsedAutofillFields): Dataset {
        val label = buildString {
            append(entry.title)
            if (entry.username.isNotBlank()) append(" — ").append(entry.username)
            if (entry.type == EntryType.CARD) append(" · Card")
            if (!entry.totpSecret.isNullOrBlank()) append(" · 2FA")
        }
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }
        val builder = Dataset.Builder(presentation)
        val intent = Intent(this, AutofillFillActivity::class.java).apply {
            putExtra(EXTRA_ENTRY_ID, entry.id)
            putParsedExtras(parsed)
        }
        val pending = PendingIntent.getActivity(
            this,
            entry.id.hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or mutable(),
        )
        builder.setAuthentication(pending.intentSender)
        builder.applyNullValues(parsed)
        return builder.build()
    }

    private fun mutable(): Int =
        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_USERNAME_ID = "username_id"
        const val EXTRA_PASSWORD_ID = "password_id"
        const val EXTRA_CARD_NUMBER_ID = "card_number_id"
        const val EXTRA_CARD_EXPIRY_ID = "card_expiry_id"
        const val EXTRA_CARD_CVV_ID = "card_cvv_id"
        const val EXTRA_CARD_HOLDER_ID = "card_holder_id"
        const val EXTRA_SAVE_USERNAME = "save_username"
        const val EXTRA_SAVE_PASSWORD = "save_password"
        private const val REQ_UNLOCK = 42
        private const val REQ_SWITCH_VAULT = 43

        fun filledDataset(
            packageName: String,
            entry: VaultEntry,
            parsed: ParsedAutofillFields,
        ): Dataset {
            val label = entry.title
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, label)
            }
            val builder = Dataset.Builder(presentation)
            parsed.usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.username)) }
            parsed.passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.password)) }
            if (entry.type == EntryType.CARD || parsed.hasCardFields) {
                parsed.cardNumberId?.let {
                    builder.setValue(it, AutofillValue.forText(entry.password))
                }
                parsed.cardExpiryId?.let {
                    builder.setValue(it, AutofillValue.forText(entry.cardExpiry))
                }
                parsed.cardCvvId?.let {
                    builder.setValue(it, AutofillValue.forText(entry.cardCvv))
                }
                parsed.cardHolderId?.let {
                    builder.setValue(it, AutofillValue.forText(entry.username))
                }
            }
            if (entry.type == EntryType.IDENTITY) {
                parsed.usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.username)) }
                parsed.passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.password)) }
            }
            return builder.build()
        }
    }
}

fun Intent.putParsedExtras(parsed: ParsedAutofillFields) {
    putExtra(KeyicAutofillService.EXTRA_PACKAGE, parsed.packageName)
    putExtra(KeyicAutofillService.EXTRA_DOMAIN, parsed.webDomain)
    putExtra(KeyicAutofillService.EXTRA_USERNAME_ID, parsed.usernameId)
    putExtra(KeyicAutofillService.EXTRA_PASSWORD_ID, parsed.passwordId)
    putExtra(KeyicAutofillService.EXTRA_CARD_NUMBER_ID, parsed.cardNumberId)
    putExtra(KeyicAutofillService.EXTRA_CARD_EXPIRY_ID, parsed.cardExpiryId)
    putExtra(KeyicAutofillService.EXTRA_CARD_CVV_ID, parsed.cardCvvId)
    putExtra(KeyicAutofillService.EXTRA_CARD_HOLDER_ID, parsed.cardHolderId)
}

internal fun Dataset.Builder.applyNullValues(parsed: ParsedAutofillFields) {
    parsed.usernameId?.let { setValue(it, null) }
    parsed.passwordId?.let { setValue(it, null) }
    parsed.cardNumberId?.let { setValue(it, null) }
    parsed.cardExpiryId?.let { setValue(it, null) }
    parsed.cardCvvId?.let { setValue(it, null) }
    parsed.cardHolderId?.let { setValue(it, null) }
}
