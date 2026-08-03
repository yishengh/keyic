package com.yishenghuang.keyic.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.port.ImportExportPort
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Writes encrypted JSON backups into a user-chosen SAF tree
 * (local folder / Google Drive / OneDrive via the system picker).
 */
class SafBackupManager(
    private val context: Context,
    private val importExportPort: ImportExportPort,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyic_saf", Context.MODE_PRIVATE)

    fun hasPassphrase(): Boolean = prefs.contains(KEY_WRAPPED)

    fun savePassphrase(passphrase: CharArray) {
        val bytes = passphrase.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(bytes)
            prefs.edit()
                .putString(KEY_WRAPPED, Base64.encodeToString(ct, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        } finally {
            bytes.fill(0)
            passphrase.fill('\u0000')
        }
    }

    fun clearPassphrase() {
        prefs.edit().remove(KEY_WRAPPED).remove(KEY_IV).apply()
    }

    fun loadPassphrase(): CharArray? {
        val wrapped = prefs.getString(KEY_WRAPPED, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            val plain = cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
            val chars = plain.toString(Charsets.UTF_8).toCharArray()
            plain.fill(0)
            chars
        } catch (_: Exception) {
            null
        }
    }

    suspend fun writeBackup(
        treeUriString: String,
        entries: List<VaultEntry>,
        vaultId: String = "default",
        attachments: List<com.yishenghuang.keyic.core.port.JsonBackupAttachment> = emptyList(),
    ): Boolean {
        val passphrase = loadPassphrase() ?: return false
        val bytes = try {
            importExportPort.exportEncryptedJson(entries, passphrase, attachments)
        } catch (_: Exception) {
            return false
        }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return false
        val fileName = backupFileName(vaultId)
        val existing = tree.findFile(fileName)
        val target = existing ?: tree.createFile("application/octet-stream", fileName)
            ?: return false
        return try {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) {
            return (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        private const val KEY_WRAPPED = "saf_pass_wrapped"
        private const val KEY_IV = "saf_pass_iv"
        private const val ALIAS = "keyic_saf_pass"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun backupFileName(vaultId: String): String =
            if (vaultId == "default" || vaultId == com.yishenghuang.keyic.core.model.LEGACY_VAULT_ID) {
                "keyic-autosync.keyic"
            } else {
                "keyic-autosync-$vaultId.keyic"
            }
    }
}
