package com.example.device

import com.example.data.model.VoicePersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSanitizerAndPersonaTest {

    @Test
    fun sanitizeTextForTts_stripsMarkdownEmojisBracketsUrlsAndSymbols() {
        val raw = "হ্যালো! **কেমন আছো**? #সাথী _টেস্ট_ ~পুরনো~ `code` (নোট) [বিস্তারিত](https://example.com/path?x=1) https://google.com www.test.org 😊✨🚀 — ভালো!"
        val cleaned = sanitizeTextForTts(raw)

        // Verify no symbols, brackets, markdown characters, URLs, or emojis remain
        val forbidden = listOf("*", "#", "_", "`", "~", "(", ")", "[", "]", "{", "}", "<", ">", "!", "?", ":", ";", "-", "—", "https://", "www.", "😊", "✨", "🚀", "।")
        forbidden.forEach { token ->
            assertFalse("Sanitized TTS text must not contain '$token', got: $cleaned", cleaned.contains(token))
        }

        // Verify readable Bengali/English words & link label are preserved naturally
        assertTrue(cleaned.contains("হ্যালো"))
        assertTrue(cleaned.contains("কেমন আছো"))
        assertTrue(cleaned.contains("সাথী"))
        assertTrue(cleaned.contains("বিস্তারিত"))
        assertTrue(cleaned.contains("ভালো"))
    }

    @Test
    fun sanitizeTextForTts_preservesDecimalNumbersAndReturnsEmptyForOnlyEmojisOrUrls() {
        assertEquals("আজ তাপমাত্রা 3.5 ডিগ্রি", sanitizeTextForTts("আজ তাপমাত্রা 3.5 ডিগ্রি 🌡️!"))
        assertEquals("tag", sanitizeTextForTts("#tag"))
        assertEquals("", sanitizeTextForTts("https://example.com 😊✨🎉 ### ***"))
    }

    @Test
    fun voicePersonas_useNaturalNonRoboticPitchAndSpeechRate() {
        VoicePersona.entries.forEach { persona ->
            assertTrue(
                "Pitch ${persona.pitch} for ${persona.key} should stay in natural range [0.85f, 1.15f]",
                persona.pitch in 0.85f..1.15f
            )
            assertTrue(
                "SpeechRate ${persona.speechRate} for ${persona.key} should stay in natural range [0.90f, 1.10f]",
                persona.speechRate in 0.90f..1.10f
            )
        }
    }
}
