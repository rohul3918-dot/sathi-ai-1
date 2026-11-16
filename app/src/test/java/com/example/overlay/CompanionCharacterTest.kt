package com.example.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CompanionCharacterCatalog
import com.example.data.repository.CompanionConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompanionCharacterTest {

    @Test
    fun companionCatalog_resolvesAllFiveUserSuppliedCharacterSlotsWithoutDefaultFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(5, CompanionCharacterCatalog.slots.size)
        for (slot in 1..5) {
            val resId = CompanionCharacterCatalog.resolveDrawableId(context, slot)
            assertTrue("Expected drawable resource for user-supplied character_$slot", resId != 0)
        }

        // Turning off (0) or invalid slots never fall back to any hardcoded default artwork
        assertEquals(0, CompanionCharacterCatalog.resolveDrawableId(context, 0))
        assertEquals(0, CompanionCharacterCatalog.resolveDrawableId(context, 6))
        assertEquals(0, CompanionCharacterCatalog.resolveDrawableId(context, -1))
    }

    @Test
    fun companionConfigStore_persistsCharacterSlotSelectionOffAndOverlayToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CompanionConfigStore(context)

        store.selectSlot(4)
        store.setFloatingOverlayEnabled(true)

        val reloaded = CompanionConfigStore(context).current()
        assertEquals(4, reloaded.selectedSlot)
        assertTrue(reloaded.floatingOverlayEnabled)

        store.selectSlot(0)
        store.setFloatingOverlayEnabled(false)
        val turnedOff = CompanionConfigStore(context).current()
        assertEquals(0, turnedOff.selectedSlot)
        assertFalse(turnedOff.floatingOverlayEnabled)
    }
}
