package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.TimelineClip
import com.example.core.TimelineMarker
import com.example.core.TimelineTrack
import com.example.core.TrackType
import com.example.core.TransitionType
import com.example.editor.EditorUiState
import com.example.ui.theme.ReaCutAmber
import com.example.ui.theme.ReaCutBgDarkest
import com.example.ui.theme.ReaCutBgPrimary
import com.example.ui.theme.ReaCutBorder
import com.example.ui.theme.ReaCutCyan
import com.example.ui.theme.ReaCutPlayheadCoral
import com.example.ui.theme.ReaCutSurface
import com.example.ui.theme.ReaCutSurfaceElevated
import com.example.ui.theme.ReaCutTeal
import com.example.ui.theme.ReaCutTextMuted
import com.example.ui.theme.ReaCutTextPrimary
import com.example.ui.theme.ReaCutTextSecondary
import com.example.ui.theme.TrackAudioGreen
import com.example.ui.theme.TrackAudioGreenDark
import com.example.ui.theme.TrackEffectPink
import com.example.ui.theme.TrackEffectPinkDark
import com.example.ui.theme.TrackOverlayPurple
import com.example.ui.theme.TrackOverlayPurpleDark
import com.example.ui.theme.TrackTextAmber
import com.example.ui.theme.TrackTextAmberDark
import com.example.ui.theme.TrackVideoBlue
import com.example.ui.theme.TrackVideoBlueDark
import kotlin.math.roundToLong

@Composable
fun MultiTrackTimeline(
  state: EditorUiState,
  onSeekTo: (Long) -> Unit,
  onSelectClip: (String) -> Unit,
  onToggleTrackMute: (String) -> Unit,
  onToggleTrackLock: (String) -> Unit,
  onToggleTrackHidden: (String) -> Unit,
  onAdjustClipTrim: (deltaStartMs: Long, deltaDurationMs: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val horizontalScroll = rememberScrollState()
  val verticalScroll = rememberScrollState()
  val density = LocalDensity.current

  val dpPerSec = state.timelineZoomPxPerSec.dp
  val totalTimelineSec = (state.project.totalDurationMs / 1000f).coerceAtLeast(10f) + 4f
  val totalWidthDp: Dp = dpPerSec * totalTimelineSec
  val pxPerMs = with(density) { dpPerSec.toPx() / 1000f }

  val trackHeaderWidth = 88.dp
  val rulerHeight = 26.dp
  val trackRowHeight = 42.dp

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(ReaCutBgDarkest)
      .border(1.dp, ReaCutBorder)
      .testTag("multitrack_timeline"),
  ) {
    // Left Pinned Column: Corner Timecode Box + Track Headers
    Column(
      modifier = Modifier
        .width(trackHeaderWidth)
        .fillMaxHeight()
        .background(ReaCutSurface)
        .border(0.5.dp, ReaCutBorder),
    ) {
      // Top-left corner header matching ruler height
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(rulerHeight)
          .background(ReaCutBgDarkest)
          .border(0.5.dp, ReaCutBorder)
          .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(
          text = "TRACKS (${state.project.tracks.size})",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
          color = ReaCutTextSecondary,
        )
      }

      // Vertically synced Track Headers
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(verticalScroll),
      ) {
        state.project.tracks.forEach { track ->
          TrackHeaderCell(
            track = track,
            height = trackRowHeight,
            onToggleMute = { onToggleTrackMute(track.id) },
            onToggleLock = { onToggleTrackLock(track.id) },
            onToggleHidden = { onToggleTrackHidden(track.id) },
          )
        }
      }
    }

    // Right Scrollable Area: Time Ruler + Track Lanes + Playhead Needle
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .horizontalScroll(horizontalScroll),
    ) {
      Column(
        modifier = Modifier
          .width(totalWidthDp)
          .fillMaxHeight(),
      ) {
        // Time Ruler with touch-scrubbing and Markers
        TimeRulerBar(
          totalDurationMs = (totalTimelineSec * 1000).toLong(),
          markers = state.project.markers,
          dpPerSec = dpPerSec,
          pxPerMs = pxPerMs,
          height = rulerHeight,
          onSeekTo = onSeekTo,
        )

        // Track Lanes
        Column(
          modifier = Modifier
            .width(totalWidthDp)
            .weight(1f)
            .verticalScroll(verticalScroll),
        ) {
          state.project.tracks.forEach { track ->
            TrackLaneRow(
              track = track,
              selectedClipId = state.selectedClipId,
              dpPerSec = dpPerSec,
              pxPerMs = pxPerMs,
              rowHeight = trackRowHeight,
              totalWidthDp = totalWidthDp,
              onSelectClip = onSelectClip,
              onSeekTo = onSeekTo,
              onAdjustClipTrim = onAdjustClipTrim,
            )
          }
        }
      }

      // Coral Red Playhead Needle Overlay spanning full height
      val playheadOffsetDp = dpPerSec * (state.playheadMs / 1000f)
      Canvas(
        modifier = Modifier
          .width(totalWidthDp)
          .fillMaxHeight(),
      ) {
        val xPx = (state.playheadMs * pxPerMs).coerceAtLeast(0f)
        // Playhead vertical line
        drawLine(
          color = ReaCutPlayheadCoral,
          start = Offset(xPx, 0f),
          end = Offset(xPx, size.height),
          strokeWidth = 2.dp.toPx(),
        )
        // Top Playhead cap polygon
        val capHalfW = 6.dp.toPx()
        val capH = 12.dp.toPx()
        val path = Path().apply {
          moveTo(xPx - capHalfW, 0f)
          lineTo(xPx + capHalfW, 0f)
          lineTo(xPx + capHalfW, capH * 0.55f)
          lineTo(xPx, capH)
          lineTo(xPx - capHalfW, capH * 0.55f)
          close()
        }
        drawPath(path = path, color = ReaCutPlayheadCoral)
      }
    }
  }
}

@Composable
private fun TrackHeaderCell(
  track: TimelineTrack,
  height: Dp,
  onToggleMute: () -> Unit,
  onToggleLock: () -> Unit,
  onToggleHidden: () -> Unit,
) {
  val accent = trackAccentColor(track.type)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .background(ReaCutSurface)
      .border(0.5.dp, ReaCutBorder)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(height - 10.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(accent),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Column {
        Text(
          text = track.type.code,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
          ),
          color = accent,
        )
        Text(
          text = "${track.clips.size}c",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
          color = ReaCutTextMuted,
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (track.type == TrackType.AUDIO || track.type == TrackType.VIDEO) {
        Box(
          modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggleMute)
            .testTag("track_mute_${track.type.code}"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Mute ${track.type.code}",
            tint = if (track.isMuted) ReaCutPlayheadCoral else ReaCutTextSecondary,
            modifier = Modifier.size(13.dp),
          )
        }
      } else {
        Box(
          modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggleHidden)
            .testTag("track_hide_${track.type.code}"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (track.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = "Hide ${track.type.code}",
            tint = if (track.isHidden) ReaCutPlayheadCoral else ReaCutTextSecondary,
            modifier = Modifier.size(13.dp),
          )
        }
      }

      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onToggleLock)
          .testTag("track_lock_${track.type.code}"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
          contentDescription = "Lock ${track.type.code}",
          tint = if (track.isLocked) ReaCutAmber else ReaCutTextMuted,
          modifier = Modifier.size(13.dp),
        )
      }
    }
  }
}

@Composable
private fun TimeRulerBar(
  totalDurationMs: Long,
  markers: List<TimelineMarker>,
  dpPerSec: Dp,
  pxPerMs: Float,
  height: Dp,
  onSeekTo: (Long) -> Unit,
) {
  val totalSec = (totalDurationMs / 1000L).toInt().coerceAtLeast(10)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .background(ReaCutBgDarkest)
      .border(0.5.dp, ReaCutBorder)
      .pointerInput(pxPerMs) {
        detectTapGestures { offset ->
          val targetMs = (offset.x / pxPerMs).roundToLong().coerceAtLeast(0L)
          onSeekTo(targetMs)
        }
      }
      .pointerInput(pxPerMs) {
        detectHorizontalDragGestures { change, _ ->
          change.consume()
          val targetMs = (change.position.x / pxPerMs).roundToLong().coerceAtLeast(0L)
          onSeekTo(targetMs)
        }
      }
      .testTag("timeline_ruler"),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      for (sec in 0..totalSec) {
        val x = sec * 1000f * pxPerMs
        drawLine(
          color = ReaCutBorder,
          start = Offset(x, size.height * 0.45f),
          end = Offset(x, size.height),
          strokeWidth = 1.5f,
        )
        // Half-second minor tick
        val halfX = (sec + 0.5f) * 1000f * pxPerMs
        drawLine(
          color = ReaCutBorder.copy(alpha = 0.5f),
          start = Offset(halfX, size.height * 0.7f),
          end = Offset(halfX, size.height),
          strokeWidth = 1f,
        )
      }

      // Draw Marker Flags
      markers.forEach { marker ->
        val mx = marker.timeMs * pxPerMs
        val mColor = Color(marker.colorHex)
        drawCircle(
          color = mColor,
          radius = 4.dp.toPx(),
          center = Offset(mx, 6.dp.toPx()),
        )
        drawLine(
          color = mColor.copy(alpha = 0.7f),
          start = Offset(mx, 6.dp.toPx()),
          end = Offset(mx, size.height),
          strokeWidth = 1.5f,
        )
      }
    }

    // Second Labels
    for (sec in 0..totalSec step 2) {
      Text(
        text = String.format("%02d:%02d", sec / 60, sec % 60),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = ReaCutTextSecondary,
        modifier = Modifier
          .offset(x = dpPerSec * sec + 3.dp, y = 2.dp),
      )
    }
  }
}

@Composable
private fun TrackLaneRow(
  track: TimelineTrack,
  selectedClipId: String?,
  dpPerSec: Dp,
  pxPerMs: Float,
  rowHeight: Dp,
  totalWidthDp: Dp,
  onSelectClip: (String) -> Unit,
  onSeekTo: (Long) -> Unit,
  onAdjustClipTrim: (Long, Long) -> Unit,
) {
  Box(
    modifier = Modifier
      .width(totalWidthDp)
      .height(rowHeight)
      .background(if (track.isLocked) ReaCutBgDarkest.copy(alpha = 0.6f) else ReaCutBgPrimary)
      .border(0.5.dp, ReaCutBorder.copy(alpha = 0.65f))
      .pointerInput(pxPerMs) {
        detectTapGestures { offset ->
          val clickedMs = (offset.x / pxPerMs).roundToLong().coerceAtLeast(0L)
          onSeekTo(clickedMs)
        }
      },
  ) {
    track.clips.forEach { clip ->
      val startDp = dpPerSec * (clip.timelineStartMs / 1000f)
      val widthDp = (dpPerSec * (clip.durationMs / 1000f)).coerceAtLeast(24.dp)
      val isSelected = clip.id == selectedClipId

      TimelineClipBlock(
        clip = clip,
        isSelected = isSelected,
        isTrackLocked = track.isLocked,
        startDp = startDp,
        widthDp = widthDp,
        rowHeight = rowHeight,
        pxPerMs = pxPerMs,
        onSelect = { onSelectClip(clip.id) },
        onAdjustTrim = onAdjustClipTrim,
      )
    }
  }
}

@Composable
private fun TimelineClipBlock(
  clip: TimelineClip,
  isSelected: Boolean,
  isTrackLocked: Boolean,
  startDp: Dp,
  widthDp: Dp,
  rowHeight: Dp,
  pxPerMs: Float,
  onSelect: () -> Unit,
  onAdjustTrim: (Long, Long) -> Unit,
) {
  val primaryColor = trackAccentColor(clip.trackType)
  val darkColor = trackDarkBgColor(clip.trackType)

  Box(
    modifier = Modifier
      .offset(x = startDp, y = 3.dp)
      .width(widthDp)
      .height(rowHeight - 6.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) darkColor.copy(alpha = 0.95f) else darkColor.copy(alpha = 0.72f))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) ReaCutCyan else primaryColor.copy(alpha = 0.75f),
        shape = RoundedCornerShape(6.dp),
      )
      .clickable(onClick = onSelect)
      .testTag("timeline_clip_${clip.id}"),
  ) {
    // Top Color Accent Bar
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .background(primaryColor),
    )

    // Audio Waveform or Keyframe Diamonds
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (clip.trackType == TrackType.AUDIO) {
        val midY = size.height * 0.6f
        val step = 6.dp.toPx()
        var x = 4f
        var idx = 0
        while (x < size.width - 4f) {
          val h = ((kotlin.math.sin(idx * 0.9f) * 0.4f + 0.5f) * size.height * 0.35f).coerceAtLeast(2f)
          drawLine(
            color = primaryColor.copy(alpha = 0.55f),
            start = Offset(x, midY - h),
            end = Offset(x, midY + h),
            strokeWidth = 2f,
          )
          x += step
          idx++
        }
      }

      // Render Keyframe Diamonds ♦ along bottom of clip
      clip.keyframes.forEach { kf ->
        val ratio = (kf.offsetMs.toFloat() / clip.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val kx = ratio * size.width
        val ky = size.height - 6.dp.toPx()
        val r = 4.dp.toPx()
        val diamond = Path().apply {
          moveTo(kx, ky - r)
          lineTo(kx + r, ky)
          lineTo(kx, ky + r)
          lineTo(kx - r, ky)
          close()
        }
        drawPath(diamond, color = ReaCutAmber)
      }
    }

    // Clip Title & Badges
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f, fill = false),
      ) {
        if (clip.transition.type != TransitionType.NONE) {
          Box(
            modifier = Modifier
              .padding(end = 4.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(ReaCutCyan.copy(alpha = 0.25f))
              .padding(horizontal = 3.dp, vertical = 1.dp),
          ) {
            Text(
              text = "⇄",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
              color = ReaCutCyan,
            )
          }
        }
        Text(
          text = clip.overlayText.ifBlank { clip.name },
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = ReaCutTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (clip.speed != 1.0f || clip.isReversed || clip.isFrozen) {
        val badge = buildString {
          if (clip.isFrozen) append("❄ ")
          if (clip.isReversed) append("◀ ")
          if (clip.speed != 1.0f) append("${clip.speed}x")
        }
        Text(
          text = badge,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
          color = ReaCutAmber,
        )
      }
    }

    // Interactive Trim Handles when clip is selected
    if (isSelected && !isTrackLocked) {
      // Left Trim Handle
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .width(10.dp)
          .fillMaxHeight()
          .background(ReaCutCyan.copy(alpha = 0.85f))
          .pointerInput(clip.id, pxPerMs) {
            detectHorizontalDragGestures { change, dragAmount ->
              change.consume()
              val deltaMs = (dragAmount / pxPerMs).roundToLong()
              if (kotlin.math.abs(deltaMs) > 15L) {
                onAdjustTrim(deltaMs, -deltaMs)
              }
            }
          },
      )
      // Right Trim Handle
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .width(10.dp)
          .fillMaxHeight()
          .background(ReaCutCyan.copy(alpha = 0.85f))
          .pointerInput(clip.id, pxPerMs) {
            detectHorizontalDragGestures { change, dragAmount ->
              change.consume()
              val deltaMs = (dragAmount / pxPerMs).roundToLong()
              if (kotlin.math.abs(deltaMs) > 15L) {
                onAdjustTrim(0L, deltaMs)
              }
            }
          },
      )
    }
  }
}

fun trackAccentColor(type: TrackType): Color = when (type) {
  TrackType.VIDEO -> TrackVideoBlue
  TrackType.OVERLAY -> TrackOverlayPurple
  TrackType.TEXT -> TrackTextAmber
  TrackType.EFFECT -> TrackEffectPink
  TrackType.AUDIO -> TrackAudioGreen
}

fun trackDarkBgColor(type: TrackType): Color = when (type) {
  TrackType.VIDEO -> TrackVideoBlueDark
  TrackType.OVERLAY -> TrackOverlayPurpleDark
  TrackType.TEXT -> TrackTextAmberDark
  TrackType.EFFECT -> TrackEffectPinkDark
  TrackType.AUDIO -> TrackAudioGreenDark
}
