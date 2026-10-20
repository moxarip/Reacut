package com.example.core

import java.util.UUID

enum class AspectRatioPreset(val label: String, val ratioWidth: Int, val ratioHeight: Int) {
  RATIO_16_9("16:9 Landscape", 16, 9),
  RATIO_9_16("9:16 Vertical", 9, 16),
  RATIO_1_1("1:1 Square", 1, 1),
  RATIO_4_3("4:3 Classic", 4, 3),
  RATIO_21_9("21:9 Cinema", 21, 9);

  val aspectFloat: Float
    get() = ratioWidth.toFloat() / ratioHeight.toFloat()
}

enum class ResolutionPreset(val label: String, val baseShortSide: Int, val bitrateBps: Int) {
  RES_720P("720p HD", 720, 4_000_000),
  RES_1080P("1080p FHD", 1080, 8_000_000),
  RES_2K("2K QHD", 1440, 14_000_000),
  RES_4K("4K UHD", 2160, 24_000_000);

  fun computeDimensions(aspect: AspectRatioPreset): Pair<Int, Int> {
    val rawW: Int
    val rawH: Int
    if (aspect.ratioWidth >= aspect.ratioHeight) {
      rawH = baseShortSide
      rawW = (baseShortSide * aspect.ratioWidth) / aspect.ratioHeight
    } else {
      rawW = baseShortSide
      rawH = (baseShortSide * aspect.ratioHeight) / aspect.ratioWidth
    }
    // Align to 16-pixel macroblocks for MediaCodec H.264 compatibility
    val alignedW = (rawW / 16) * 16
    val alignedH = (rawH / 16) * 16
    return alignedW.coerceAtLeast(320) to alignedH.coerceAtLeast(320)
  }
}

enum class TrackType(val code: String, val displayName: String) {
  VIDEO("V1", "Main Video"),
  OVERLAY("V2", "PIP Overlay"),
  TEXT("T1", "Subtitles & Text"),
  EFFECT("FX", "LUT & Effects"),
  AUDIO("A1", "Audio & BGM"),
}

enum class ProceduralClipTheme(val title: String, val primaryHex: Long, val secondaryHex: Long) {
  CYBERPUNK_GRID("Cyberpunk Grid", 0xFF00E5FF, 0xFF7C4DFF),
  NEON_CITY("Neon City Horizon", 0xFFFF4081, 0xFF1DE9B6),
  STUDIO_COUNTDOWN("SMPTE Leader Countdown", 0xFFFFAB00, 0xFF2979FF),
  WAVEFORM_SYNTH("Audio Spectrum Pulse", 0xFF00E676, 0xFF00B0FF),
  SUNSET_HORIZON("Retrowave Sunset", 0xFFFF6D00, 0xFFD500F9),
  MATRIX_STREAM("Digital Code Matrix", 0xFF00C853, 0xFF004D40),
}

enum class FilterPreset(
  val displayName: String,
  val code: String,
  val contrast: Float = 1f,
  val saturation: Float = 1f,
  val brightness: Float = 0f,
  val temperature: Float = 0f,
  val tintR: Float = 1f,
  val tintG: Float = 1f,
  val tintB: Float = 1f,
) {
  NONE("Original (None)", "none"),
  CYBERPUNK_CYAN("Cyberpunk Cyan", "cyber_cyan", contrast = 1.2f, saturation = 1.25f, tintR = 0.88f, tintG = 1.05f, tintB = 1.18f),
  TEAL_ORANGE("Teal & Orange Blockbuster", "teal_orange", contrast = 1.18f, saturation = 1.15f, temperature = 0.18f, tintR = 1.08f, tintG = 0.98f, tintB = 1.06f),
  VINTAGE_FILM("Kodak 35mm Vintage", "vintage_film", contrast = 0.92f, saturation = 0.82f, brightness = 0.04f, temperature = 0.22f, tintR = 1.08f, tintG = 1.02f, tintB = 0.88f),
  NOIR_BW("Cinema Noir B&W", "noir_bw", contrast = 1.35f, saturation = 0.0f, brightness = -0.03f),
  WARM_SUNSET("Golden Hour Warmth", "warm_sunset", contrast = 1.08f, saturation = 1.2f, temperature = 0.35f, tintR = 1.12f, tintG = 1.0f, tintB = 0.86f),
  COLD_NORDIC("Nordic Frost", "cold_nordic", contrast = 1.1f, saturation = 0.78f, temperature = -0.32f, tintR = 0.88f, tintG = 0.98f, tintB = 1.15f),
  MATRIX_GREEN("MatrixPhosphor", "matrix_green", contrast = 1.25f, saturation = 0.65f, tintR = 0.75f, tintG = 1.22f, tintB = 0.82f),
  VHS_GLITCH("1994 VHS Tape", "vhs_glitch", contrast = 0.9f, saturation = 1.35f, brightness = 0.05f, tintR = 1.1f, tintG = 0.94f, tintB = 1.12f),
  HIGH_CONTRAST("Studio Punch HDR", "punch_hdr", contrast = 1.4f, saturation = 1.3f, brightness = -0.02f),
  SEPIA_GOLD("Archival Sepia", "sepia_gold", contrast = 1.05f, saturation = 0.35f, temperature = 0.4f, tintR = 1.15f, tintG = 1.02f, tintB = 0.78f),
  PASTEL_DREAM("Soft Pastel Air", "pastel_dream", contrast = 0.86f, saturation = 0.9f, brightness = 0.08f, tintR = 1.04f, tintG = 1.02f, tintB = 1.08f),
}

enum class TransitionType(val displayName: String, val capCutKey: String) {
  NONE("None (Cut)", "none"),
  CROSS_DISSOLVE("Cross Dissolve", "dissolve"),
  DIP_TO_BLACK("Dip to Black", "fade_black"),
  DIP_TO_WHITE("Dip to White", "fade_white"),
  SLIDE_LEFT("Whip Slide Left", "slide_left"),
  ZOOM_IN("Dynamic Zoom", "zoom_in"),
  GLITCH_FLASH("Cyber Glitch", "glitch_flash"),
}

enum class EasingType(val displayName: String) {
  LINEAR("Linear"),
  EASE_IN("Ease In"),
  EASE_OUT("Ease Out"),
  EASE_IN_OUT("Ease In/Out"),
}

data class ColorGrading(
  val brightness: Float = 0f, // -0.5f .. 0.5f
  val contrast: Float = 1f, // 0.4f .. 2.2f
  val saturation: Float = 1f, // 0f .. 2.5f
  val temperature: Float = 0f, // -0.6f .. 0.6f
  val vignette: Float = 0f, // 0f .. 1f
  val sharpness: Float = 0f, // 0f .. 1f
  val filterPreset: FilterPreset = FilterPreset.NONE,
)

data class TransitionConfig(
  val type: TransitionType = TransitionType.NONE,
  val durationMs: Long = 600L,
)

data class ClipKeyframe(
  val id: String = UUID.randomUUID().toString(),
  val offsetMs: Long, // Relative to clip start (0..durationMs)
  val scale: Float = 1f,
  val positionX: Float = 0f, // Normalized -1f .. 1f
  val positionY: Float = 0f, // Normalized -1f .. 1f
  val rotationDeg: Float = 0f, // -180f .. 180f
  val opacity: Float = 1f, // 0f .. 1f
  val easing: EasingType = EasingType.EASE_IN_OUT,
)

data class TimelineClip(
  val id: String = UUID.randomUUID().toString(),
  val materialId: String = UUID.randomUUID().toString(),
  val name: String,
  val trackType: TrackType,
  val timelineStartMs: Long,
  val durationMs: Long,
  val sourceInMs: Long = 0L,
  val sourceDurationMs: Long = 15_000L,
  val sourceUri: String? = null,
  val proceduralTheme: ProceduralClipTheme = ProceduralClipTheme.CYBERPUNK_GRID,
  val overlayText: String = "",
  val speed: Float = 1.0f,
  val isReversed: Boolean = false,
  val isFrozen: Boolean = false,
  // Transform
  val scale: Float = 1.0f,
  val positionX: Float = 0.0f,
  val positionY: Float = 0.0f,
  val rotationDeg: Float = 0.0f,
  val opacity: Float = 1.0f,
  val mirrorHorizontal: Boolean = false,
  val mirrorVertical: Boolean = false,
  // Audio
  val volume: Float = 1.0f,
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  // Color & FX
  val colorGrading: ColorGrading = ColorGrading(),
  val transition: TransitionConfig = TransitionConfig(),
  // Keyframes
  val keyframes: List<ClipKeyframe> = emptyList(),
) {
  val timelineEndMs: Long
    get() = timelineStartMs + durationMs
}

data class TimelineTrack(
  val id: String = UUID.randomUUID().toString(),
  val type: TrackType,
  val name: String = type.displayName,
  val isMuted: Boolean = false,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val clips: List<TimelineClip> = emptyList(),
)

enum class SubtitleAnimation(val displayName: String) {
  NONE("None"),
  POP_IN("Pop In"),
  FADE_UP("Fade Up"),
  TYPEWRITER("Typewriter"),
}

data class SubtitleStyle(
  val fontSizeSp: Float = 18f,
  val textColorHex: Long = 0xFFFFFFFF,
  val strokeColorHex: Long = 0xFF000000,
  val strokeWidth: Float = 3f,
  val bgColorHex: Long = 0xFF0D1117,
  val bgOpacity: Float = 0.65f,
  val verticalPosition: Float = 0.84f, // 0.1 (top) to 0.92 (bottom)
  val isBold: Boolean = true,
  val isItalic: Boolean = false,
  val showBilingual: Boolean = true,
  val animation: SubtitleAnimation = SubtitleAnimation.POP_IN,
)

data class SubtitleCue(
  val id: String = UUID.randomUUID().toString(),
  val startTimeMs: Long,
  val endTimeMs: Long,
  val text: String,
  val secondaryText: String = "",
  val speaker: String = "",
  val style: SubtitleStyle = SubtitleStyle(),
) {
  val durationMs: Long
    get() = (endTimeMs - startTimeMs).coerceAtLeast(100L)
}

data class TimelineMarker(
  val id: String = UUID.randomUUID().toString(),
  val timeMs: Long,
  val label: String,
  val colorHex: Long = 0xFF00E5FF,
)

data class ProjectConfig(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "ReaCut Studio Project",
  val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9,
  val resolution: ResolutionPreset = ResolutionPreset.RES_1080P,
  val fps: Int = 30,
  val canvasColorHex: Long = 0xFF090D14,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class DraftImportReport(
  val sourceFormat: String,
  val isEncryptedError: Boolean = false,
  val importedVideoCount: Int = 0,
  val importedAudioCount: Int = 0,
  val importedSubtitleCount: Int = 0,
  val importedEffectCount: Int = 0,
  val totalTimelineDurationMs: Long = 0L,
  val warnings: List<String> = emptyList(),
)

data class ReaCutProject(
  val config: ProjectConfig = ProjectConfig(),
  val tracks: List<TimelineTrack> = emptyList(),
  val subtitles: List<SubtitleCue> = emptyList(),
  val markers: List<TimelineMarker> = emptyList(),
  val lastImportReport: DraftImportReport? = null,
) {
  val totalDurationMs: Long
    get() {
      val maxClipEnd = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 0L
      val maxSubEnd = subtitles.maxOfOrNull { it.endTimeMs } ?: 0L
      return maxOf(maxClipEnd, maxSubEnd, 5_000L)
    }
}
