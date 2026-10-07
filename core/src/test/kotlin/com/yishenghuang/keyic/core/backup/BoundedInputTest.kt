package com.yishenghuang.keyic.core.backup

import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsBoundaryAndRejectsOversize() {
        assertEquals(16, BoundedInput.read(ByteArray(16).inputStream(), 16).size)
        assertTrue(runCatching { BoundedInput.read(ByteArray(17).inputStream(), 16) }.isFailure)
        assertTrue(BoundedInput.read(ByteArray(0).inputStream(), 16).isEmpty())
    }
}
