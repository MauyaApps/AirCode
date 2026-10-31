package com.mauyaapps.aircode.theme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeStyle { BASE, MATERIAL_YOU, CUSTOM, GLASS }

const val DEFAULT_ACCENT: Int = 0xFF3F7CFF.toInt()

const val MIN_CONTRAST: Float = -1f
const val MAX_CONTRAST: Float = 1f

data class ColorSlot(
    val color: Int,
    val contrast: Float = 0f,
)

data class YouColors(
    val primary: ColorSlot = ColorSlot(DEFAULT_ACCENT),
    val secondary: ColorSlot? = null,
    val tertiary: ColorSlot? = null,
    val neutral: ColorSlot? = null,
)

data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.BASE,
    val accent: Int = DEFAULT_ACCENT,
    val youCustom: Boolean = false,
    val youColors: YouColors = YouColors(),
)

val ACCENT_PRESETS: List<Int> = listOf(
    0xFF3F7CFF.toInt(), 0xFF00A8C6.toInt(), 0xFF2E9E5B.toInt(), 0xFF8DB600.toInt(), 0xFFE0A100.toInt(),
    0xFFF57C00.toInt(), 0xFFE53935.toInt(), 0xFFD81B8C.toInt(), 0xFF8E44D6.toInt(), 0xFF4F46E5.toInt(),
)
