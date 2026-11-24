package org.aiguardian.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLocalizerEmptySceneTest {
    @Test
    fun englishEmptyViewMessageDoesNotClaimPathIsSafe() {
        val message = SpeechLocalizer.localizeNoObjectsDetected("English (US)")
        assertTrue(message.contains("No supported object"))
        assertTrue(message.contains("does not confirm"))
        assertTrue(message.contains("safe"))
    }

    @Test
    fun urduEmptyViewMessageIncludesSafetyLimitation() {
        val message = SpeechLocalizer.localizeNoObjectsDetected("Urdu (Pakistan)")
        assertTrue(message.contains("کوئی قابلِ شناخت چیز"))
        assertTrue(message.contains("محفوظ ہونے کی تصدیق نہیں"))
    }
}
