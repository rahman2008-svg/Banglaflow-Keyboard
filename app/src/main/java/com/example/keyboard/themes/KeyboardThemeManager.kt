package com.example.keyboard.themes

import androidx.compose.ui.graphics.Color
import com.example.database.CustomTheme

data class ThemeColors(
    val name: String,
    val backgroundColor: Color,
    val keyColor: Color,
    val textColor: Color,
    val cornerRadius: Int = 8,
    val isDark: Boolean = true
)

object KeyboardThemeManager {
    val presets = listOf(
        ThemeColors(
            "AMOLED Dark",
            Color(0xFF000000),
            Color(0xFF1E1E1E),
            Color(0xFFFFFFFF),
            cornerRadius = 10,
            isDark = true
        ),
        ThemeColors(
            "Neon Mint",
            Color(0xFF0D1B1E),
            Color(0xFF1F3A3A),
            Color(0xFF2EC4B6),
            cornerRadius = 8,
            isDark = true
        ),
        ThemeColors(
            "Ocean Breeze",
            Color(0xFF1C2D37),
            Color(0xFF2C4554),
            Color(0xFFE2F1F8),
            cornerRadius = 8,
            isDark = true
        ),
        ThemeColors(
            "Sunset Glow",
            Color(0xFF2B1B17),
            Color(0xFF4A2B20),
            Color(0xFFFFB7B2),
            cornerRadius = 12,
            isDark = true
        ),
        ThemeColors(
            "Pastel Pearl",
            Color(0xFFF7F7F9),
            Color(0xFFE3E3E6),
            Color(0xFF2B2D42),
            cornerRadius = 8,
            isDark = false
        ),
        ThemeColors(
            "Cyberpunk",
            Color(0xFF1F0E1C),
            Color(0xFF4A103D),
            Color(0xFFFF0055),
            cornerRadius = 4,
            isDark = true
        )
    )

    fun resolveTheme(entity: CustomTheme?): ThemeColors {
        if (entity == null) return presets[0] // Default to AMOLED Dark
        
        return ThemeColors(
            name = entity.name,
            backgroundColor = Color(entity.backgroundColor),
            keyColor = Color(entity.keyColor),
            textColor = Color(entity.textColor),
            cornerRadius = entity.cornerRadius,
            isDark = entity.isDark
        )
    }
}
