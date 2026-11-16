package com.example.data.model

import android.content.Context
import androidx.annotation.DrawableRes

data class CompanionCharacterSlot(
    val slotNumber: Int,
    val resourceName: String,
    val label: String
)

data class CompanionPreferences(
    val selectedSlot: Int = 1,
    val floatingOverlayEnabled: Boolean = false
)

object CompanionCharacterCatalog {
    val slots: List<CompanionCharacterSlot> = listOf(
        CompanionCharacterSlot(1, "character_1", "character_1"),
        CompanionCharacterSlot(2, "character_2", "character_2"),
        CompanionCharacterSlot(3, "character_3", "character_3"),
        CompanionCharacterSlot(4, "character_4", "character_4"),
        CompanionCharacterSlot(5, "character_5", "character_5")
    )

    @DrawableRes
    fun resolveDrawableId(context: Context, slotNumber: Int): Int {
        if (slotNumber !in 1..5) return 0
        val resName = "character_$slotNumber"
        return try {
            context.resources.getIdentifier(resName, "drawable", context.packageName)
        } catch (_: Exception) {
            0
        }
    }

    fun firstAvailableSlot(context: Context): Int {
        return slots.firstOrNull { resolveDrawableId(context, it.slotNumber) != 0 }?.slotNumber ?: 0
    }
}
