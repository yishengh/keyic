package com.yishenghuang.keyic.data

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.data.backup.EncryptedJsonBackupPort
import com.yishenghuang.keyic.data.backup.SafBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Local-only provider for exercising real ContentResolver/DocumentFile writes and failures. */
class TestDocumentsProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY = "com.yishenghuang.keyic.data.test.documents"
        @Volatile var failWrite = false
    }
    private val root get() = File(context!!.filesDir, "synthetic-saf").apply { mkdirs() }
    private val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE)
    override fun onCreate() = true
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == "root" && !documentId.contains('/') && !documentId.contains('\\') && File(root, documentId).isFile
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(emptyArray())
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(columns).apply { addRow(row(documentId)) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(columns).apply { root.listFiles().orEmpty().forEach { addRow(row(it.name)) } }
    private fun row(id: String): Array<Any> = arrayOf(id, id,
        if (id == "root") DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream",
        if (id == "root") DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
        if (id == "root") 0L else File(root, id).length())
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(parentDocumentId == "root" && !displayName.contains('/') && !displayName.contains('\\'))
        File(root, displayName).createNewFile()
        return displayName
    }
    override fun deleteDocument(documentId: String) { File(root, documentId).delete() }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (failWrite && mode.contains('w')) throw FileNotFoundException("Synthetic provider failure")
        return ParcelFileDescriptor.open(File(root, documentId), ParcelFileDescriptor.parseMode(mode))
    }
}

@RunWith(AndroidJUnit4::class)
class SafBackupTest {
    @Test fun completedBackupsRemainReadableAfterLaterWriteFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val port = EncryptedJsonBackupPort()
        val manager = SafBackupManager(context, port)
        val password = UUID.randomUUID().toString()
        manager.savePassphrase(password.toCharArray())
        val tree = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root").toString()
        val vaultId = UUID.randomUUID().toString()
        val entry = VaultEntry("test", "Synthetic SAF", createdAt = 1, updatedAt = 1, passwordChangedAt = 1)
        val folder = File(context.filesDir, "synthetic-saf")
        val prefix = "keyic-autosync-$vaultId"
        TestDocumentsProvider.failWrite = false
        assertTrue(manager.writeBackup(tree, listOf(entry), vaultId))
        assertTrue(manager.writeBackup(tree, listOf(entry), vaultId))
        val before = folder.listFiles()!!.filter { it.name.startsWith(prefix) }.associate { it.name to it.readBytes() }
        assertEquals(2, before.size)
        try {
            TestDocumentsProvider.failWrite = true
            assertFalse(manager.writeBackup(tree, listOf(entry), vaultId))
        } finally { TestDocumentsProvider.failWrite = false }
        val after = folder.listFiles()!!.filter { it.name.startsWith(prefix) }
        assertEquals(2, after.size)
        for (file in after) {
            assertTrue(before.getValue(file.name).contentEquals(file.readBytes()))
            assertTrue(port.importEncryptedJson(file.readBytes(), password.toCharArray()).entries.single() == entry)
        }
    }
}
