package com.yishenghuang.keyic.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.data.crypto.VaultKeyManager
import javax.crypto.Cipher

object BiometricUnlock {
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Enroll biometric unlock: authenticate, then wrap [dbKey] with the KeyStore key.
     * Caller should zero [dbKey] after the callbacks fire.
     */
    fun enroll(
        activity: FragmentActivity,
        keyManager: VaultKeyManager,
        dbKey: ByteArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!canAuthenticate(activity)) {
            onError(activity.getString(R.string.biometrics_unavailable))
            return
        }
        // Drop a broken leftover key so enrollment can recreate it.
        if (!keyManager.isBiometricEnabled()) {
            keyManager.disableBiometric()
        }
        val cipher: Cipher = try {
            keyManager.createBiometricCipherForEncrypt()
        } catch (e: Exception) {
            onError(e.message ?: activity.getString(R.string.error_biometric_failed))
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val crypto = result.cryptoObject?.cipher
                    if (crypto == null) {
                        onError(activity.getString(R.string.error_biometric_failed))
                        return
                    }
                    val ok = keyManager.completeBiometricWrap(crypto, dbKey)
                    if (ok) onSuccess() else onError(activity.getString(R.string.error_biometric_failed))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    } else {
                        onError(activity.getString(R.string.error_cancelled))
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt open for another try; no toast spam.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_enable_title))
            .setSubtitle(activity.getString(R.string.biometric_enable_subtitle))
            .setNegativeButtonText(activity.getString(R.string.action_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    fun prompt(
        activity: FragmentActivity,
        keyManager: VaultKeyManager,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!keyManager.isBiometricEnabled()) {
            onError(activity.getString(R.string.biometric_not_enabled))
            return
        }
        val cipher: Cipher = try {
            keyManager.createBiometricCipherForDecrypt()
        } catch (e: Exception) {
            onError(e.message ?: activity.getString(R.string.error_biometric_failed))
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val crypto = result.cryptoObject?.cipher
                    if (crypto == null) {
                        onError(activity.getString(R.string.error_biometric_failed))
                        return
                    }
                    try {
                        onSuccess(keyManager.unlockWithBiometricCipher(crypto))
                    } catch (e: Exception) {
                        onError(e.message ?: activity.getString(R.string.error_biometric_failed))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_unlock_title))
            .setSubtitle(activity.getString(R.string.biometric_unlock_subtitle))
            .setNegativeButtonText(activity.getString(R.string.biometric_use_password))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
