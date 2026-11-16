package com.example.data.repository

import android.content.Context
import com.example.data.model.CompanionCharacterCatalog
import com.example.data.model.CompanionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompanionConfigStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitial())
    val state: StateFlow<CompanionPreferences> = _state.asStateFlow()

    private fun loadInitial(): CompanionPreferences {
        val defaultSlot = CompanionCharacterCatalog.firstAvailableSlot(appContext)
        val rawSlot = if (prefs.contains(KEY_SELECTED_SLOT)) {
            prefs.getInt(KEY_SELECTED_SLOT, defaultSlot)
        } else {
            defaultSlot
        }
        val validSlot = if (rawSlot == 0) {
            0
        } else if (rawSlot in 1..5 && CompanionCharacterCatalog.resolveDrawableId(appContext, rawSlot) != 0) {
            rawSlot
        } else {
            defaultSlot
        }
        val overlayEnabled = prefs.getBoolean(KEY_FLOATING_OVERLAY_ENABLED, false)
        return CompanionPreferences(
            selectedSlot = validSlot,
            floatingOverlayEnabled = overlayEnabled
        )
    }

    fun current(): CompanionPreferences = _state.value

    fun selectSlot(slotNumber: Int) {
        val normalized = if (slotNumber in 1..5 &&
            CompanionCharacterCatalog.resolveDrawableId(appContext, slotNumber) != 0
        ) {
            slotNumber
        } else {
            0
        }
        val next = _state.value.copy(selectedSlot = normalized)
        prefs.edit().putInt(KEY_SELECTED_SLOT, next.selectedSlot).apply()
        _state.value = next
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        val next = _state.value.copy(floatingOverlayEnabled = enabled)
        prefs.edit().putBoolean(KEY_FLOATING_OVERLAY_ENABLED, next.floatingOverlayEnabled).apply()
        _state.value = next
    }

    fun refresh() {
        _state.value = loadInitial()
    }

    companion object {
        private const val PREFS_NAME = "sathi_companion_prefs"
        private const val KEY_SELECTED_SLOT = "selected_character_slot"
        private const val KEY_FLOATING_OVERLAY_ENABLED = "floating_overlay_enabled"
    }
}
