package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMigrationTest {
    @Test fun `legacy international URL and authenticated field migrate to DashScope preset`() {
        var decryptedField = ""
        val migrated = migrateLegacyAsr(
            "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference",
            "old-ciphertext",
        ) { ciphertext, field ->
            assertEquals("old-ciphertext", ciphertext)
            decryptedField = field
            "dashscope-secret"
        }
        assertEquals("asr_key_encrypted", decryptedField)
        assertEquals(DashScopeRegion.Singapore, migrated.region)
        assertEquals("dashscope-secret", migrated.key)
    }

    @Test fun `legacy default URL migrates to Beijing`() {
        val migrated = migrateLegacyAsr("", "") { _, _ -> "" }
        assertEquals(DashScopeRegion.Beijing, migrated.region)
    }

    @Test fun `unknown persisted service falls back to default`() {
        assertEquals(AsrService.Default, AsrService.fromId("removed-service"))
    }
}
