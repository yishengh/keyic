package com.yishenghuang.keyic.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import java.util.Locale

data class ParsedAutofillFields(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val cardNumberId: AutofillId?,
    val cardExpiryId: AutofillId?,
    val cardCvvId: AutofillId?,
    val cardHolderId: AutofillId?,
    val packageName: String?,
    val webDomain: String?,
) {
    val hasLoginFields: Boolean get() = usernameId != null || passwordId != null
    val hasCardFields: Boolean
        get() = cardNumberId != null || cardExpiryId != null || cardCvvId != null || cardHolderId != null
    val hasAnyFillable: Boolean get() = hasLoginFields || hasCardFields
}

data class ExtractedCredentials(
    val username: String,
    val password: String,
    val packageName: String?,
    val webDomain: String?,
)

object AutofillStructureParser {
    private val usernameHints = setOf(
        View.AUTOFILL_HINT_USERNAME,
        View.AUTOFILL_HINT_EMAIL_ADDRESS,
        "username",
        "email",
        "emailaddress",
        "newusername",
        "current-username",
        "accountname",
        "login",
        "userid",
        "user_id",
        "phone",
        "tel",
    )
    private val passwordHints = setOf(
        View.AUTOFILL_HINT_PASSWORD,
        "password",
        "current-password",
        "new-password",
        "newpassword",
        "currentpassword",
        "passwd",
        "pwd",
    )
    private val cardNumberHints = setOf(
        View.AUTOFILL_HINT_CREDIT_CARD_NUMBER,
        "creditcardnumber",
        "cc-number",
        "cardnumber",
        "card_number",
    )
    private val cardExpiryHints = setOf(
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE,
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH,
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR,
        "cc-exp",
        "cc-exp-month",
        "cc-exp-year",
        "cardexpiry",
        "expiry",
        "expiration",
    )
    private val cardCvvHints = setOf(
        View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE,
        "cc-csc",
        "cvv",
        "cvc",
        "securitycode",
        "cardsecuritycode",
    )
    private val cardHolderHints = setOf(
        "personname",
        "name",
        "cc-name",
        "ccname",
        "cardholder",
        "card_holder",
        "nameoncard",
    )
    private val usernameAutocomplete = setOf(
        "username",
        "email",
        "tel",
        "tel-national",
        "nickname",
        "name",
        "current-username",
        "webauthn",
    )
    private val passwordAutocomplete = setOf(
        "current-password",
        "new-password",
        "password",
    )

    fun parse(structure: AssistStructure): ParsedAutofillFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var cardNumberId: AutofillId? = null
        var cardExpiryId: AutofillId? = null
        var cardCvvId: AutofillId? = null
        var cardHolderId: AutofillId? = null
        var webDomain: String? = null
        val packageName = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode ?: continue
            traverse(root) { node ->
                if (webDomain == null) {
                    webDomain = node.webDomain
                }
                val id = node.autofillId ?: return@traverse
                when (classify(node)) {
                    FieldKind.Password -> if (passwordId == null) passwordId = id
                    FieldKind.Username -> if (usernameId == null) usernameId = id
                    FieldKind.CardNumber -> if (cardNumberId == null) cardNumberId = id
                    FieldKind.CardExpiry -> if (cardExpiryId == null) cardExpiryId = id
                    FieldKind.CardCvv -> if (cardCvvId == null) cardCvvId = id
                    FieldKind.CardHolder -> if (cardHolderId == null) cardHolderId = id
                    FieldKind.Other -> Unit
                }
            }
        }

        return ParsedAutofillFields(
            usernameId = usernameId,
            passwordId = passwordId,
            cardNumberId = cardNumberId,
            cardExpiryId = cardExpiryId,
            cardCvvId = cardCvvId,
            cardHolderId = cardHolderId,
            packageName = packageName,
            webDomain = webDomain,
        )
    }

    fun extractCredentials(structure: AssistStructure): ExtractedCredentials? {
        var username = ""
        var password = ""
        var webDomain: String? = null
        val packageName = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode ?: continue
            traverse(root) { node ->
                if (webDomain == null) webDomain = node.webDomain
                val text = nodeText(node) ?: return@traverse
                when (classify(node)) {
                    FieldKind.Password -> if (password.isEmpty()) password = text
                    FieldKind.Username -> if (username.isEmpty()) username = text
                    else -> Unit
                }
            }
        }
        if (username.isBlank() && password.isBlank()) return null
        return ExtractedCredentials(username, password, packageName, webDomain)
    }

    private enum class FieldKind {
        Username, Password, CardNumber, CardExpiry, CardCvv, CardHolder, Other
    }

    private fun classify(node: AssistStructure.ViewNode): FieldKind {
        val hints = node.autofillHints?.map { it.lowercase(Locale.US) }.orEmpty()
        val html = node.htmlInfo?.attributes
            ?.associate { it.first.lowercase(Locale.US) to it.second.orEmpty() }
            .orEmpty()
        val autocomplete = html["autocomplete"].orEmpty().lowercase(Locale.US)
            .split(' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val hintText = buildString {
            append(node.hint?.toString().orEmpty())
            append(' ')
            append(node.idEntry.orEmpty())
            append(' ')
            append(html["name"].orEmpty())
            append(' ')
            append(html["type"].orEmpty())
            append(' ')
            append(html["autocomplete"].orEmpty())
        }.lowercase(Locale.US)

        fun hintsMatch(set: Set<String>) =
            hints.any { h -> set.any { h.contains(it) } } ||
                autocomplete.any { it in set } ||
                set.any { hintText.contains(it) }

        if (hintsMatch(cardNumberHints) || hintText.contains("card number") ||
            html["autocomplete"].equals("cc-number", true)
        ) {
            return FieldKind.CardNumber
        }
        if (hintsMatch(cardCvvHints)) return FieldKind.CardCvv
        if (hintsMatch(cardExpiryHints)) return FieldKind.CardExpiry
        if (hintsMatch(cardHolderHints) &&
            (hintText.contains("card") || autocomplete.any { it.startsWith("cc-") })
        ) {
            return FieldKind.CardHolder
        }

        val isPassword = hints.any { h -> passwordHints.any { h.contains(it) } } ||
            autocomplete.any { it in passwordAutocomplete } ||
            isPasswordInput(node.inputType) ||
            hintText.contains("password") ||
            hintText.contains("passwd") ||
            html["type"].equals("password", ignoreCase = true)

        if (isPassword) return FieldKind.Password

        val isUsername = hints.any { h -> usernameHints.any { h.contains(it) } } ||
            autocomplete.any { it in usernameAutocomplete } ||
            hintText.contains("user") ||
            hintText.contains("email") ||
            hintText.contains("login") ||
            hintText.contains("phone") ||
            html["type"].equals("email", ignoreCase = true) ||
            html["type"].equals("tel", ignoreCase = true)

        return if (isUsername) FieldKind.Username else FieldKind.Other
    }

    private fun nodeText(node: AssistStructure.ViewNode): String? {
        val autofill = node.textValue()?.takeIf { it.isNotBlank() }
        if (autofill != null) return autofill
        return node.text?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun AssistStructure.ViewNode.textValue(): String? {
        val value = autofillValue ?: return null
        return if (value.isText) value.textValue?.toString() else null
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
    }

    private fun traverse(node: AssistStructure.ViewNode, visit: (AssistStructure.ViewNode) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            traverse(node.getChildAt(i), visit)
        }
    }
}
