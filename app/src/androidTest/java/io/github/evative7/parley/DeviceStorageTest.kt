package io.github.evative7.parley

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.evative7.parley.data.KeyVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceStorageTest {
    @Test fun keystoreRoundTripRandomIvAndFieldBinding() {
        val vault = KeyVault()
        val secret = "instrumentation-" + UUID.randomUUID()
        val first = vault.encrypt(secret, "device-test")
        val second = vault.encrypt(secret, "device-test")
        assertNotEquals(first, second)
        assertFalse(first.contains(secret))
        assertEquals(secret, KeyVault().decrypt(first, "device-test"))
        try {
            vault.decrypt(first, "other-field")
            fail("Ciphertext must be bound to its field")
        } catch (_: GeneralSecurityException) {
            // Expected: changing AAD invalidates the GCM tag.
        }
    }

}
