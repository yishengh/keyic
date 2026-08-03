package com.yishenghuang.keyic.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.yishenghuang.keyic.core.model.LEGACY_VAULT_ID
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Derives wrapping keys with Argon2id and protects the SQLCipher DB key.
 * Biometric unlock uses Android KeyStore (AES/GCM, BIOMETRIC_STRONG).
 * Scoped per [vaultId] — legacy vault keeps original prefs / KeyStore alias names.
 */
class VaultKeyManager(
    context: Context,
    private val vaultId: String,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName(vaultId), Context.MODE_PRIVATE)
    private val bioAlias: String = bioAlias(vaultId)
    private val argon2 = Argon2Kt()
    private val secureRandom = SecureRandom()

    fun isConfigured(): Boolean = prefs.contains(KEY_WRAPPED_DB) && prefs.contains(KEY_SALT)

    fun setupWithPassword(masterPassword: CharArray): ByteArray {
        val dbKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val wrappingKey = deriveKey(masterPassword, salt)
        try {
            val wrapped = aesGcmEncrypt(wrappingKey, dbKey)
            prefs.edit()
                .putString(KEY_SALT, b64(salt))
                .putString(KEY_WRAPPED_DB, b64(wrapped))
                .putInt(KEY_ARGON_M, MEMORY_KIB)
                .putInt(KEY_ARGON_T, ITERATIONS)
                .putInt(KEY_ARGON_P, PARALLELISM)
                .apply()
            return dbKey.copyOf()
        } finally {
            wrappingKey.fill(0)
            masterPassword.fill('\u0000')
        }
    }

    fun unlockWithPassword(masterPassword: CharArray): ByteArray? {
        val salt = prefs.getString(KEY_SALT, null)?.let { unb64(it) } ?: return null
        val wrapped = prefs.getString(KEY_WRAPPED_DB, null)?.let { unb64(it) } ?: return null
        val wrappingKey = deriveKey(masterPassword, salt)
        return try {
            aesGcmDecrypt(wrappingKey, wrapped)
        } catch (_: Exception) {
            null
        } finally {
            wrappingKey.fill(0)
            masterPassword.fill('\u0000')
        }
    }

    /**
     * Prepare an encrypt cipher bound to the biometric KeyStore key.
     * Must be passed to [BiometricPrompt] as a [BiometricPrompt.CryptoObject]
     * before [completeBiometricWrap].
     */
    fun createBiometricCipherForEncrypt(): Cipher {
        val secretKey = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    /** Persist vault DB key wrapped by an authenticated biometric cipher. */
    fun completeBiometricWrap(cipher: Cipher, dbKey: ByteArray): Boolean {
        return try {
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(dbKey)
            prefs.edit()
                .putString(KEY_BIO_WRAPPED, b64(ciphertext))
                .putString(KEY_BIO_IV, b64(iv))
                .putBoolean(KEY_BIO_ENABLED, true)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Legacy helper — prefer [createBiometricCipherForEncrypt] + biometric prompt +
     * [completeBiometricWrap]. Direct encrypt fails when the key requires user auth.
     */
    fun enableBiometricWrap(dbKey: ByteArray): Boolean {
        return try {
            val cipher = createBiometricCipherForEncrypt()
            completeBiometricWrap(cipher, dbKey)
        } catch (_: Exception) {
            false
        }
    }

    fun disableBiometric() {
        prefs.edit()
            .remove(KEY_BIO_WRAPPED)
            .remove(KEY_BIO_IV)
            .putBoolean(KEY_BIO_ENABLED, false)
            .apply()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(bioAlias)) ks.deleteEntry(bioAlias)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun isBiometricEnabled(): Boolean =
        prefs.getBoolean(KEY_BIO_ENABLED, false) && prefs.contains(KEY_BIO_WRAPPED)

    fun createBiometricCipherForDecrypt(): Cipher {
        val secretKey = getOrCreateBiometricKey()
        val iv = unb64(prefs.getString(KEY_BIO_IV, null)!!)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher
    }

    fun unlockWithBiometricCipher(cipher: Cipher): ByteArray {
        val wrapped = unb64(prefs.getString(KEY_BIO_WRAPPED, null)!!)
        return cipher.doFinal(wrapped)
    }

    fun changeMasterPassword(currentDbKey: ByteArray, newPassword: CharArray): Boolean {
        if (newPassword.size < 8) {
            newPassword.fill('\u0000')
            return false
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val wrappingKey = deriveKey(newPassword.copyOf(), salt)
        return try {
            val wrapped = aesGcmEncrypt(wrappingKey, currentDbKey)
            prefs.edit()
                .putString(KEY_SALT, b64(salt))
                .putString(KEY_WRAPPED_DB, b64(wrapped))
                .putInt(KEY_ARGON_M, MEMORY_KIB)
                .putInt(KEY_ARGON_T, ITERATIONS)
                .putInt(KEY_ARGON_P, PARALLELISM)
                .apply()
            // Biometric wrap encrypts the same DB key — no re-wrap needed.
            true
        } catch (_: Exception) {
            false
        } finally {
            wrappingKey.fill(0)
            newPassword.fill('\u0000')
        }
    }

    /** Wipe all key material for this vault (used on delete). */
    fun wipe() {
        disableBiometric()
        prefs.edit().clear().apply()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val memory = prefs.getInt(KEY_ARGON_M, MEMORY_KIB)
        val iterations = prefs.getInt(KEY_ARGON_T, ITERATIONS)
        val parallelism = prefs.getInt(KEY_ARGON_P, PARALLELISM)
        val passwordBytes = password.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = salt,
                tCostInIterations = iterations,
                mCostInKibibyte = memory,
                parallelism = parallelism,
                hashLengthInBytes = 32,
            )
            result.rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    private fun aesGcmDecrypt(key: ByteArray, blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(bioAlias)) {
            return (ks.getEntry(bioAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            bioAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG,
                    )
                }
            }
            .setIsStrongBoxBacked(tryStrongBox())
            .build()
        return try {
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            val fallback = KeyGenParameterSpec.Builder(
                bioAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG,
                        )
                    }
                }
                .build()
            keyGenerator.init(fallback)
            keyGenerator.generateKey()
        }
    }

    private fun tryStrongBox(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 28

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_WRAPPED_DB = "wrapped_db"
        private const val KEY_ARGON_M = "argon_m"
        private const val KEY_ARGON_T = "argon_t"
        private const val KEY_ARGON_P = "argon_p"
        private const val KEY_BIO_WRAPPED = "bio_wrapped"
        private const val KEY_BIO_IV = "bio_iv"
        private const val KEY_BIO_ENABLED = "bio_enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MEMORY_KIB = 32 * 1024
        private const val ITERATIONS = 3
        private const val PARALLELISM = 2

        fun prefsName(vaultId: String): String =
            if (vaultId == LEGACY_VAULT_ID) "keyic_vault_meta" else "keyic_vault_meta_$vaultId"

        fun bioAlias(vaultId: String): String =
            if (vaultId == LEGACY_VAULT_ID) "keyic_biometric_wrap" else "keyic_biometric_wrap_$vaultId"

        /** Probe whether the legacy single-vault prefs exist (pre multi-vault). */
        fun isLegacyConfigured(context: Context): Boolean {
            val prefs = context.applicationContext
                .getSharedPreferences(prefsName(LEGACY_VAULT_ID), Context.MODE_PRIVATE)
            return prefs.contains(KEY_WRAPPED_DB) && prefs.contains(KEY_SALT)
        }
    }
}
