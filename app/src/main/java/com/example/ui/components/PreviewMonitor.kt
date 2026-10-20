package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.KeyframeColorEngine
import com.example.core.RealMp4ExportEngine
import com.example.core.TrackType
import com.example.editor.EditorUiState
import com.example.ui.theme.ReaCutAmber
import com.example.ui.theme.ReaCutBgDarkest
import com.example.ui.theme.ReaCutBorder
import com.example.ui.theme.ReaCutCyan
import com.example.ui.theme.ReaCutPlayheadCoral
import com.example.ui.theme.ReaCutSurface
import com.example.ui.theme.ReaCutSurfaceElevated
import com.example.ui.theme.ReaCutTeal
import com.example.ui.theme.ReaCutTextMuted
import com.example.ui.theme.ReaCutTextPrimary
import com.example.ui.theme.ReaCutTextSecondary

@Composable
fun PreviewMonitor(
  state: EditorUiState,
  onTogglePlayPause: () -> Unit,
  onStepBackward: () -> Unit,
  onStepForward: () -> Unit,
  onJumpStart: () -> Unit,
  onJumpEnd: () -> Unit,
  onToggleLoop: () -> Unit,
  onToggleSafeGuides: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val aspectRatio = state.project.config.aspectRatio.aspectFloat

  // Check if there is an active device video URI at playheadMs
  val activeDeviceClip = state.project.tracks
    .filter { (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) && !it.isHidden }
    .flatMap { it.clips }
    .firstOrNull {
      it.sourceUri != null && state.playheadMs in it.timelineStartMs until it.timelineEndMs
    }

  val exoPlayer = remember(context) {
    ExoPlayer.Builder(context).build().apply {
      repeatMode = Player.REPEAT_MODE_ONE
    }
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  LaunchedEffect(activeDeviceClip?.sourceUri) {
    val uriStr = activeDeviceClip?.sourceUri
    if (!uriStr.isNullOrBlank()) {
      runCatching {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
        exoPlayer.prepare()
      }
    } else {
      exoPlayer.stop()
    }
  }

  LaunchedEffect(state.isPlaying, activeDeviceClip?.id) {
    if (activeDeviceClip?.sourceUri != null) {
      if (state.isPlaying) exoPlayer.play() else exoPlayer.pause()
    } else {
      exoPlayer.pause()
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(ReaCutBgDarkest)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Monitor Viewport Container
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF06090E))
        .border(1.dp, ReaCutBorder, RoundedCornerShape(8.dp))
        .testTag("preview_monitor_canvas"),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
          .clip(RoundedCornerShape(4.dp))
          .border(1.dp, ReaCutBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
      ) {
        // If a real device video URI is active on the playhead, display ExoPlayer underneath the overlay canvas
        if (activeDeviceClip?.sourceUri != null) {
          AndroidView(
            factory = { ctx ->
              PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Real-time NLE Frame Compositor (renders procedural themes, LUT filters, keyframes, transitions, and subtitles)
        Canvas(modifier = Modifier.fillMaxSize()) {
          if (activeDeviceClip?.sourceUri == null) {
            RealMp4ExportEngine.renderCompositeFrameToCanvas(
              context = context,
              canvas = drawContext.canvas.nativeCanvas,
              width = size.width,
              height = size.height,
              project = state.project,
              playheadMs = state.playheadMs,
              burnInSubtitles = true,
            )
          }

          // Optional Broadcast Safe-Area & Rule-of-Thirds Guides
          if (state.showSafeGuides) {
            val guideColor = Color.White.copy(alpha = 0.28f)
            // Rule of thirds
            drawLine(guideColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1f)
            drawLine(guideColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1f)
            drawLine(guideColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1f)
            drawLine(guideColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1f)
            // 90% Title Safe Box
            val insetX = size.width * 0.05f
            val insetY = size.height * 0.05f
            drawRect(
              color = ReaCutCyan.copy(alpha = 0.35f),
              topLeft = Offset(insetX, insetY),
              size = Size(size.width * 0.9f, size.height * 0.9f),
              style = Stroke(width = 1.5f),
            )
          }
        }
      }

      // Top-Right Active Clip & LUT HUD Badge
      val selected = state.selectedClip
      if (selected != null) {
        Row(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ReaCutBgDarkest.copy(alpha = 0.78f))
            .border(1.dp, ReaCutBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = selected.colorGrading.filterPreset.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = ReaCutCyan,
          )
          if (selected.keyframes.isNotEmpty()) {
            Text(
              text = "♦ ${selected.keyframes.size} KF",
              style = MaterialTheme.typography.labelSmall,
              color = ReaCutAmber,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Transport Controls & SMPTE Timecode Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(36.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(ReaCutSurface)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // SMPTE Timecode Readout
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = KeyframeColorEngine.formatTimecode(state.playheadMs, state.project.config.fps),
          style = MaterialTheme.typography.labelMedium,
          color = ReaCutPlayheadCoral,
          modifier = Modifier.testTag("timecode_current"),
        )
        Text(
          text = " / ${KeyframeColorEngine.formatTimecode(state.project.totalDurationMs, state.project.config.fps)}",
          style = MaterialTheme.typography.labelSmall,
          color = ReaCutTextSecondary,
        )
      }

      // Transport Buttons
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onJumpStart,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_jump_start"),
        ) {
          Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "Jump to Start",
            tint = ReaCutTextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onStepBackward,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_step_back"),
        ) {
          Icon(
            imageVector = Icons.Default.FastRewind,
            contentDescription = "Step Back 1 Frame",
            tint = ReaCutTextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (state.isPlaying) ReaCutPlayheadCoral else ReaCutCyan)
            .testTag("play_pause_button"),
        ) {
          Icon(
            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            tint = ReaCutBgDarkest,
            modifier = Modifier.size(20.dp),
          )
        }

        IconButton(
          onClick = onStepForward,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_step_forward"),
        ) {
          Icon(
            imageVector = Icons.Default.FastForward,
            contentDescription = "Step Forward 1 Frame",
            tint = ReaCutTextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onJumpEnd,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_jump_end"),
        ) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "Jump to End",
            tint = ReaCutTextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      // Loop & Safe Guides Toggles
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onToggleLoop,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_loop_toggle"),
        ) {
          Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = "Toggle Loop Playback",
            tint = if (state.isLooping) ReaCutTeal else ReaCutTextMuted,
            modifier = Modifier.size(17.dp),
          )
        }
        IconButton(
          onClick = onToggleSafeGuides,
          modifier = Modifier
            .size(32.dp)
            .testTag("transport_guides_toggle"),
        ) {
          Icon(
            imageVector = Icons.Default.Grid4x4,
            contentDescription = "Toggle Safe Area Guides",
            tint = if (state.showSafeGuides) ReaCutCyan else ReaCutTextMuted,
            modifier = Modifier.size(17.dp),
          )
        }
      }
    }
  }
}
