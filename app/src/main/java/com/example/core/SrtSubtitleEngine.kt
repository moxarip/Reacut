package com.example.core

import java.util.UUID

object SrtSubtitleEngine {

  private val SRT_TIME_REGEX =
    Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")

  /**
   * Parses standard .srt or .vtt subtitle content into a list of ReaCut [SubtitleCue] items.
   * Supports bilingual subtitles (line 1 = primary text, line 2 = secondary translation).
   */
  fun parseSrtOrVtt(rawContent: String, defaultStyle: SubtitleStyle = SubtitleStyle()): List<SubtitleCue> {
    val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalized.isEmpty()) return emptyList()

    val blocks = normalized.split(Regex("\n\\s*\n"))
    val cues = mutableListOf<SubtitleCue>()

    for (block in blocks) {
      val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) continue
      if (lines.first().startsWith("WEBVTT", ignoreCase = true)) continue

      val timeLineIndex = lines.indexOfFirst { SRT_TIME_REGEX.containsMatchIn(it) }
      if (timeLineIndex == -1) continue

      val match = SRT_TIME_REGEX.find(lines[timeLineIndex]) ?: continue
      val startMs = toMillis(
        match.groupValues[1],
        match.groupValues[2],
        match.groupValues[3],
        match.groupValues[4],
      )
      val endMs = toMillis(
        match.groupValues[5],
        match.groupValues[6],
        match.groupValues[7],
        match.groupValues[8],
      )

      val textLines = lines.drop(timeLineIndex + 1)
        .map { it.replace(Regex("<[^>]*>"), "").trim() }
        .filter { it.isNotEmpty() }

      if (textLines.isEmpty()) continue

      val primaryText = textLines.first()
      val secondaryText = if (textLines.size > 1) textLines.drop(1).joinToString(" ") else ""

      cues.add(
        SubtitleCue(
          id = UUID.randomUUID().toString(),
          startTimeMs = startMs,
          endTimeMs = endMs.coerceAtLeast(startMs + 200L),
          text = primaryText,
          secondaryText = secondaryText,
          style = defaultStyle,
        )
      )
    }

    return cues.sortedBy { it.startTimeMs }
  }

  /**
   * Exports a list of [SubtitleCue] into SubRip (.srt) format.
   */
  fun exportToSrt(cues: List<SubtitleCue>, includeBilingual: Boolean = true): String {
    val sorted = cues.sortedBy { it.startTimeMs }
    val sb = StringBuilder()
    sorted.forEachIndexed { index, cue ->
      sb.append(index + 1).append('\n')
      sb.append(formatSrtTime(cue.startTimeMs))
        .append(" --> ")
        .append(formatSrtTime(cue.endTimeMs))
        .append('\n')
      sb.append(cue.text.trim()).append('\n')
      if (includeBilingual && cue.secondaryText.isNotBlank()) {
        sb.append(cue.secondaryText.trim()).append('\n')
      }
      sb.append('\n')
    }
    return sb.toString().trimEnd() + "\n"
  }

  /**
   * Exports a list of [SubtitleCue] into WebVTT (.vtt) format.
   */
  fun exportToVtt(cues: List<SubtitleCue>, includeBilingual: Boolean = true): String {
    val sorted = cues.sortedBy { it.startTimeMs }
    val sb = StringBuilder("WEBVTT\n\n")
    sorted.forEachIndexed { index, cue ->
      sb.append(index + 1).append('\n')
      sb.append(formatSrtTime(cue.startTimeMs).replace(',', '.'))
        .append(" --> ")
        .append(formatSrtTime(cue.endTimeMs).replace(',', '.'))
        .append('\n')
      sb.append(cue.text.trim()).append('\n')
      if (includeBilingual && cue.secondaryText.isNotBlank()) {
        sb.append(cue.secondaryText.trim()).append('\n')
      }
      sb.append('\n')
    }
    return sb.toString()
  }

  /**
   * Generates synchronized timeline subtitles from existing video/audio clips on the timeline.
   */
  fun autoGenerateCuesFromTracks(
    tracks: List<TimelineTrack>,
    defaultStyle: SubtitleStyle = SubtitleStyle(),
  ): List<SubtitleCue> {
    val mainClips = tracks
      .filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
      .flatMap { it.clips }
      .sortedBy { it.timelineStartMs }

    if (mainClips.isEmpty()) return emptyList()

    val generated = mutableListOf<SubtitleCue>()
    mainClips.forEachIndexed { idx, clip ->
      val label = clip.overlayText.ifBlank { clip.name }
      val halfDur = (clip.durationMs / 2L).coerceAtLeast(800L)
      generated.add(
        SubtitleCue(
          startTimeMs = clip.timelineStartMs,
          endTimeMs = (clip.timelineStartMs + halfDur).coerceAtMost(clip.timelineEndMs),
          text = "Scene ${idx + 1}: $label",
          secondaryText = "المشهد ${idx + 1}: $label",
          style = defaultStyle,
        )
      )
      if (clip.durationMs > 2200L) {
        generated.add(
          SubtitleCue(
            startTimeMs = clip.timelineStartMs + halfDur,
            endTimeMs = clip.timelineEndMs,
            text = "Filter: ${clip.colorGrading.filterPreset.displayName} • Speed ${clip.speed}x",
            secondaryText = "الفلتر: ${clip.colorGrading.filterPreset.displayName}",
            style = defaultStyle,
          )
        )
      }
    }
    return generated.sortedBy { it.startTimeMs }
  }

  fun formatSrtTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe % 3_600_000L) / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val millis = safe % 1_000L
    return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
  }

  private fun toMillis(hStr: String, mStr: String, sStr: String, msStr: String): Long {
    val h = hStr.toLongOrNull() ?: 0L
    val m = mStr.toLongOrNull() ?: 0L
    val s = sStr.toLongOrNull() ?: 0L
    val paddedMs = msStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    return h * 3_600_000L + m * 60_000L + s * 1_000L + paddedMs
  }
}
