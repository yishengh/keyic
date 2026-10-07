package com.yishenghuang.keyic.core.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bound untrusted documents before parsing/decrypting them in memory. */
object BoundedInput {
    const val MAX_BYTES = 64 * 1024 * 1024

    fun read(input: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= maxBytes) { "Document exceeds size limit" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
            output.close()
        }
    }
}
