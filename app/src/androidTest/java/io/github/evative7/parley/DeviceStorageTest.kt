package io.github.evative7.parley

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.evative7.parley.data.KeyVault
import io.github.evative7.parley.data.SettingsRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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

    /** Explicit one-time preparation only. Never overwrites an already configured key. */
    @Test fun prepareBailianPublicSettings(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt in with -e prepareBailian true", args.getString("prepareBailian") == "true")
        val repository = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext, KeyVault())
        val initial = withTimeout(10_000) { repository.settings.filterNotNull().first() }
        check(!initial.storageError && initial.asrKey.isEmpty() && initial.translationKey.isEmpty()) {
            "Refusing to overwrite existing or unreadable service credentials"
        }
        // Demo temporarily permits saving public defaults without a key. The user turns it off
        // after pasting the newly created key into both fields; cloud tests reject demo mode.
        val prepared = initial.copy(
            demoMode = true,
            translationUrl = args.getString("translationBaseUrl")
                ?: "https://dashscope.aliyuncs.com/compatible-mode/v1",
            translationModel = "qwen-plus",
        )
        repository.save(prepared)
        withTimeout(5_000) { repository.settings.filterNotNull().first { it == prepared } }
    }
}
