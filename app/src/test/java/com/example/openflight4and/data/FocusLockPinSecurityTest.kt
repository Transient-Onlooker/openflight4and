package com.example.openflight4and.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLockPinSecurityTest {

    @Test
    fun verify_returnsTrue_forMatchingPin() {
        val pinHash = FocusLockPinSecurity.createHash("1234")

        assertTrue(FocusLockPinSecurity.verify("1234", pinHash.saltBase64, pinHash.hashBase64))
    }

    @Test
    fun verify_returnsFalse_forDifferentPin() {
        val pinHash = FocusLockPinSecurity.createHash("1234")

        assertFalse(FocusLockPinSecurity.verify("9999", pinHash.saltBase64, pinHash.hashBase64))
    }
}
