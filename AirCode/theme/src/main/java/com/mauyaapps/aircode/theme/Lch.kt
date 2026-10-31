package com.mauyaapps.aircode.theme

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Lch(val lightness: Float, val chroma: Float, val hue: Float)

private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val LAB_EPSILON = 216.0 / 24389.0
private const val LAB_SLOPE = 7.787037
private const val LAB_OFFSET = 16.0 / 116.0
private const val GAMUT_TOLERANCE = 0.0005
private const val GAMUT_STEPS = 20

private fun srgbToLinear(value: Double): Double =
    if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

private fun linearToSrgb(value: Double): Double =
    if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055

private fun labForward(t: Double): Double = if (t > LAB_EPSILON) cbrt(t) else LAB_SLOPE * t + LAB_OFFSET

private fun labInverse(f: Double): Double {
    val cubed = f * f * f
    return if (cubed > LAB_EPSILON) cubed else (f - LAB_OFFSET) / LAB_SLOPE
}

fun argbToLch(argb: Int): Lch {
    val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
    val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
    val b = srgbToLinear((argb and 0xFF) / 255.0)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
    val fx = labForward(x)
    val fy = labForward(y)
    val fz = labForward(z)
    val a = 500.0 * (fx - fy)
    val bb = 200.0 * (fy - fz)
    var hue = atan2(bb, a) * 180.0 / PI
    if (hue < 0.0) hue += 360.0
    return Lch(
        lightness = (116.0 * fy - 16.0).toFloat(),
        chroma = sqrt(a * a + bb * bb).toFloat(),
        hue = hue.toFloat(),
    )
}

private fun lchToLinearRgb(lightness: Double, chroma: Double, hueDegrees: Double): DoubleArray {
    val hueRadians = hueDegrees * PI / 180.0
    val a = chroma * cos(hueRadians)
    val b = chroma * sin(hueRadians)
    val fy = (lightness + 16.0) / 116.0
    val fx = fy + a / 500.0
    val fz = fy - b / 200.0
    val x = labInverse(fx) * WHITE_X
    val y = labInverse(fy)
    val z = labInverse(fz) * WHITE_Z
    return doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
}

private fun inGamut(rgb: DoubleArray): Boolean =
    rgb.all { it >= -GAMUT_TOLERANCE && it <= 1.0 + GAMUT_TOLERANCE }

fun toneToArgb(hue: Float, chroma: Float, tone: Float): Int {
    if (tone <= 0f) return 0xFF000000.toInt()
    if (tone >= 100f) return 0xFFFFFFFF.toInt()
    val lightness = tone.toDouble()
    val hueDegrees = hue.toDouble()
    var used = chroma.coerceAtLeast(0f).toDouble()
    if (!inGamut(lchToLinearRgb(lightness, used, hueDegrees))) {
        var low = 0.0
        var high = used
        repeat(GAMUT_STEPS) {
            val middle = (low + high) / 2.0
            if (inGamut(lchToLinearRgb(lightness, middle, hueDegrees))) low = middle else high = middle
        }
        used = low
    }
    val rgb = lchToLinearRgb(lightness, used, hueDegrees)
    fun channel(value: Double): Int = (linearToSrgb(value.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
}
