package com.example.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class InterpolatedTransform(
  val scale: Float,
  val positionX: Float,
  val positionY: Float,
  val rotationDeg: Float,
  val opacity: Float,
)

data class TransitionRenderState(
  val activeTransition: TransitionType,
  val progress: Float, // 0f..1f during the transition window
  val alphaMultiplier: Float = 1f,
  val offsetXFraction: Float = 0f,
  val scaleMultiplier: Float = 1f,
  val flashOverlayAlpha: Float = 0f,
  val flashColorHex: Long = 0xFF000000,
)

object KeyframeColorEngine {

  /**
   * Formats milliseconds into SMPTE timecode HH:MM:SS:FF or MM:SS:FF
   */
  fun formatTimecode(timeMs: Long, fps: Int = 30, includeHours: Boolean = false): String {
    val safeMs = timeMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val frames = ((safeMs % 1000L) * fps.coerceAtLeast(1)) / 1000L
    return if (includeHours || hours > 0) {
      String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    } else {
      String.format("%02d:%02d:%02d", minutes, seconds, frames)
    }
  }

  /**
   * Evaluates clip transform properties at a given global playhead timestamp,
   * interpolating between keyframes if keyframes exist on the clip.
   */
  fun evaluateTransformAt(clip: TimelineClip, playheadMs: Long): InterpolatedTransform {
    val localMs = (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
    val sorted = clip.keyframes.sortedBy { it.offsetMs }
    if (sorted.isEmpty()) {
      return InterpolatedTransform(
        scale = clip.scale,
        positionX = clip.positionX,
        positionY = clip.positionY,
        rotationDeg = clip.rotationDeg,
        opacity = clip.opacity,
      )
    }
    if (localMs <= sorted.first().offsetMs) {
      val first = sorted.first()
      return InterpolatedTransform(
        scale = first.scale,
        positionX = first.positionX,
        positionY = first.positionY,
        rotationDeg = first.rotationDeg,
        opacity = first.opacity,
      )
    }
    if (localMs >= sorted.last().offsetMs) {
      val last = sorted.last()
      return InterpolatedTransform(
        scale = last.scale,
        positionX = last.positionX,
        positionY = last.positionY,
        rotationDeg = last.rotationDeg,
        opacity = last.opacity,
      )
    }

    var prev = sorted.first()
    var next = sorted.last()
    for (i in 0 until sorted.size - 1) {
      if (localMs in sorted[i].offsetMs..sorted[i + 1].offsetMs) {
        prev = sorted[i]
        next = sorted[i + 1]
        break
      }
    }

    val span = (next.offsetMs - prev.offsetMs).coerceAtLeast(1L).toFloat()
    val rawT = ((localMs - prev.offsetMs).toFloat() / span).coerceIn(0f, 1f)
    val t = applyEasing(rawT, next.easing)

    return InterpolatedTransform(
      scale = lerp(prev.scale, next.scale, t),
      positionX = lerp(prev.positionX, next.positionX, t),
      positionY = lerp(prev.positionY, next.positionY, t),
      rotationDeg = lerp(prev.rotationDeg, next.rotationDeg, t),
      opacity = lerp(prev.opacity, next.opacity, t).coerceIn(0f, 1f),
    )
  }

  /**
   * Evaluates entry transition state at the beginning of a clip.
   */
  fun evaluateTransitionState(clip: TimelineClip, playheadMs: Long): TransitionRenderState {
    val type = clip.transition.type
    val transDur = clip.transition.durationMs.coerceIn(100L, clip.durationMs)
    val localMs = (playheadMs - clip.timelineStartMs)
    if (type == TransitionType.NONE || localMs < 0L || localMs > transDur) {
      return TransitionRenderState(TransitionType.NONE, 1f)
    }
    val p = (localMs.toFloat() / transDur.toFloat()).coerceIn(0f, 1f)
    return when (type) {
      TransitionType.NONE -> TransitionRenderState(type, 1f)
      TransitionType.CROSS_DISSOLVE -> TransitionRenderState(
        activeTransition = type,
        progress = p,
        alphaMultiplier = p,
      )
      TransitionType.DIP_TO_BLACK -> TransitionRenderState(
        activeTransition = type,
        progress = p,
        alphaMultiplier = p,
        flashOverlayAlpha = (1f - p),
        flashColorHex = 0xFF000000,
      )
      TransitionType.DIP_TO_WHITE -> TransitionRenderState(
        activeTransition = type,
        progress = p,
        alphaMultiplier = p,
        flashOverlayAlpha = (1f - p),
        flashColorHex = 0xFFFFFFFF,
      )
      TransitionType.SLIDE_LEFT -> TransitionRenderState(
        activeTransition = type,
        progress = p,
        offsetXFraction = (1f - applyEasing(p, EasingType.EASE_OUT)),
      )
      TransitionType.ZOOM_IN -> TransitionRenderState(
        activeTransition = type,
        progress = p,
        alphaMultiplier = p,
        scaleMultiplier = 0.45f + 0.55f * applyEasing(p, EasingType.EASE_OUT),
      )
      TransitionType.GLITCH_FLASH -> {
        val jitter = sin(p * 36f) * (1f - p) * 0.08f
        TransitionRenderState(
          activeTransition = type,
          progress = p,
          offsetXFraction = jitter,
          scaleMultiplier = 1f + (1f - p) * 0.12f,
          flashOverlayAlpha = (1f - p) * 0.5f,
          flashColorHex = 0xFF00E5FF,
        )
      }
    }
  }

  /**
   * Computes a 20-element 4x5 ColorMatrix array combining manual color grading
   * and an active LUT FilterPreset (if any) from either the clip or an active FX track clip.
   */
  fun buildColorMatrixArray(
    grading: ColorGrading,
    activeFxPreset: FilterPreset? = null,
  ): FloatArray {
    val preset = if (activeFxPreset != null && activeFxPreset != FilterPreset.NONE) {
      activeFxPreset
    } else {
      grading.filterPreset
    }

    val totalContrast = (grading.contrast * preset.contrast).coerceIn(0.2f, 3.0f)
    val totalSaturation = (grading.saturation * preset.saturation).coerceIn(0f, 3.0f)
    val totalBrightness = (grading.brightness + preset.brightness).coerceIn(-0.8f, 0.8f) * 255f
    val totalTemp = (grading.temperature + preset.temperature).coerceIn(-1f, 1f)

    // Luminance weights for saturation
    val lumR = 0.3086f
    val lumG = 0.6094f
    val lumB = 0.0820f

    val sr = (1f - totalSaturation) * lumR
    val sg = (1f - totalSaturation) * lumG
    val sb = (1f - totalSaturation) * lumB

    // Warmth/temperature shifts Red up and Blue down
    val rScale = preset.tintR * (1f + totalTemp * 0.18f) * totalContrast
    val gScale = preset.tintG * (1f + totalTemp * 0.03f) * totalContrast
    val bScale = preset.tintB * (1f - totalTemp * 0.18f) * totalContrast

    val translate = 128f * (1f - totalContrast) + totalBrightness

    return floatArrayOf(
      (sr + totalSaturation) * rScale, sg * rScale, sb * rScale, 0f, translate,
      sr * gScale, (sg + totalSaturation) * gScale, sb * gScale, 0f, translate,
      sr * bScale, sg * bScale, (sb + totalSaturation) * bScale, 0f, translate,
      0f, 0f, 0f, 1f, 0f,
    )
  }

  private fun applyEasing(t: Float, easing: EasingType): Float {
    val clamped = t.coerceIn(0f, 1f)
    return when (easing) {
      EasingType.LINEAR -> clamped
      EasingType.EASE_IN -> clamped * clamped
      EasingType.EASE_OUT -> 1f - (1f - clamped) * (1f - clamped)
      EasingType.EASE_IN_OUT ->
        if (clamped < 0.5f) 2f * clamped * clamped
        else 1f - (-2f * clamped + 2f) * (-2f * clamped + 2f) / 2f
    }
  }

  private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
