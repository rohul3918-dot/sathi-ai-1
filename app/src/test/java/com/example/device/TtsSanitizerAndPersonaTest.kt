package com.example.device

import com.example.data.model.VoicePersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsSanitizerAndPersonaTest {

    @Test
    fun sanitizeTextForTts_stripsMarkdownEmojisBracketsUrlsAndAllPunctuation() {
        val raw = "হ্যালো, **কেমন আছো**? #সাথী _টেস্ট_ ~পুরনো~ `code` (নোট) [বিস্তারিত](https://example.com/path?x=1) https://google.com www.test.org 😊✨🚀 — ভালো! অনেক সুন্দর।"
        val cleaned = sanitizeTextForTts(raw)

        // Verify NO symbols, brackets, markdown characters, URLs, commas, full-stops, danda, or emojis remain
        val forbidden = listOf(
            "*", "#", "_", "`", "~", "(", ")", "[", "]", "{", "}", "<", ">",
            ",", "!", "?", ":", ";", "-", "—", "।", "https://", "www.", "😊", "✨", "🚀"
        )
        forbidden.forEach { token ->
            assertFalse("Sanitized TTS text must not contain '$token', got: $cleaned", cleaned.contains(token))
        }

        // Verify readable Bengali/English words & link label are preserved naturally
        assertTrue(cleaned.contains("হ্যালো"))
        assertTrue(cleaned.contains("কেমন আছো"))
        assertTrue(cleaned.contains("সাথী"))
        assertTrue(cleaned.contains("বিস্তারিত"))
        assertTrue(cleaned.contains("ভালো"))
        assertTrue(cleaned.contains("অনেক সুন্দর"))
    }

    @Test
    fun sanitizeTextForTts_preservesDecimalNumbersAndReturnsEmptyForOnlyEmojisOrUrls() {
        assertEquals("আজ তাপমাত্রা 3.5 ডিগ্রি", sanitizeTextForTts("আজ তাপমাত্রা 3.5 ডিগ্রি 🌡️!"))
        assertEquals("tag", sanitizeTextForTts("#tag"))
        assertEquals("", sanitizeTextForTts("https://example.com 😊✨🎉 ### ***"))
    }

    @Test
    fun splitSentencesForTts_splitsMultipleSentencesCleanlyWithoutSymbols() {
        val raw = "হ্যালো! আমি **সাথী** 😊। তোমার আজ দিন কেমন কাটছে? দারুণ, তাই না?"
        val segments = splitSentencesForTts(raw)
        assertEquals(listOf("হ্যালো", "আমি সাথী", "তোমার আজ দিন কেমন কাটছে", "দারুণ তাই না"), segments)
    }

    @Test
    fun classifyVoiceGender_identifiesGoogleTtsBengaliAndEnglishVoices() {
        assertEquals(VoiceGenderHint.FEMALE, classifyVoiceGender("bn-in-x-bnf-local"))
        assertEquals(VoiceGenderHint.FEMALE, classifyVoiceGender("bn-bd-x-bna-network"))
        assertEquals(VoiceGenderHint.FEMALE, classifyVoiceGender("en-us-x-sfg-local"))
        assertEquals(VoiceGenderHint.MALE, classifyVoiceGender("bn-bd-x-bgd-local"))
        assertEquals(VoiceGenderHint.MALE, classifyVoiceGender("bn-in-x-bnm-network"))
        assertEquals(VoiceGenderHint.MALE, classifyVoiceGender("en-us-x-iom-local"))
        assertEquals(VoiceGenderHint.FEMALE, classifyVoiceGender("custom-voice", setOf("gender=female")))
        assertEquals(VoiceGenderHint.UNKNOWN, classifyVoiceGender("bn-bd-default"))
    }

    @Test
    fun selectVoiceCandidate_prefersFemaleCrossDialectOverOppositeMaleLocalVoice() {
        val bnBd = Locale.forLanguageTag("bn-BD")
        val bnIn = Locale.forLanguageTag("bn-IN")
        val candidates = listOf(
            VoiceCandidate(id = "bn-bd-x-bgd-local", locale = bnBd, quality = 400, isNetworkRequired = false),
            VoiceCandidate(id = "bn-in-x-bnf-local", locale = bnIn, quality = 400, isNetworkRequired = false),
            VoiceCandidate(id = "bn-in-x-bna-network", locale = bnIn, quality = 500, isNetworkRequired = true)
        )

        val femaleSelection = selectVoiceCandidate(
            candidates = candidates,
            preferredLocale = bnBd,
            wantMale = false,
            variantIndex = 0
        )
        assertNotNull(femaleSelection)
        assertEquals(VoiceGenderHint.FEMALE, femaleSelection!!.genderHint)
        assertEquals("bn-in-x-bnf-local", femaleSelection.candidate.id)

        val maleSelection = selectVoiceCandidate(
            candidates = candidates,
            preferredLocale = bnBd,
            wantMale = true,
            variantIndex = 0
        )
        assertNotNull(maleSelection)
        assertEquals(VoiceGenderHint.MALE, maleSelection!!.genderHint)
        assertEquals("bn-bd-x-bgd-local", maleSelection.candidate.id)
    }

    @Test
    fun computeNaturalPitch_usesNaturalPitchWhenMatchedAndCompensatesWhenOpposite() {
        val femaleMatchedPitch = computeNaturalPitch(VoicePersona.FEMALE_WARM, VoiceGenderHint.FEMALE)
        val femaleCompensatedPitch = computeNaturalPitch(VoicePersona.FEMALE_WARM, VoiceGenderHint.MALE)
        assertTrue(femaleMatchedPitch in 0.95f..1.08f)
        assertTrue("Expected higher pitch compensation when only male base voice exists, got $femaleCompensatedPitch", femaleCompensatedPitch >= 1.20f)

        val maleMatchedPitch = computeNaturalPitch(VoicePersona.MALE_CALM, VoiceGenderHint.MALE)
        val maleCompensatedPitch = computeNaturalPitch(VoicePersona.MALE_CALM, VoiceGenderHint.FEMALE)
        assertTrue(maleMatchedPitch in 0.90f..1.05f)
        assertTrue("Expected lower pitch compensation when only female base voice exists, got $maleCompensatedPitch", maleCompensatedPitch <= 0.86f)
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
