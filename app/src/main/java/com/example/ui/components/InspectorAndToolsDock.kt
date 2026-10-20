package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AspectRatioPreset
import com.example.core.EasingType
import com.example.core.FilterPreset
import com.example.core.KeyframeColorEngine
import com.example.core.ProceduralClipTheme
import com.example.core.ResolutionPreset
import com.example.core.SubtitleAnimation
import com.example.core.TrackType
import com.example.core.TransitionType
import com.example.editor.EditorUiState
import com.example.editor.StudioDockTab
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
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun InspectorAndToolsDock(
  state: EditorUiState,
  onSelectTab: (StudioDockTab) -> Unit,
  onPickDeviceVideo: () -> Unit,
  onPickDeviceAudio: () -> Unit,
  onPickSrtFile: () -> Unit,
  onOpenCapCutDraftModal: () -> Unit,
  onAddProceduralClip: (ProceduralClipTheme, TrackType) -> Unit,
  onUpdateProjectConfig: (AspectRatioPreset?, ResolutionPreset?, Int?) -> Unit,
  // Subtitles
  onAddSubtitleAtPlayhead: (String, String) -> Unit,
  onUpdateSubtitleCue: (String, String, String, Long, Long) -> Unit,
  onDeleteSubtitleCue: (String) -> Unit,
  onShiftAllSubtitles: (Long) -> Unit,
  onAutoGenerateSubtitles: () -> Unit,
  onUpdateGlobalSubtitleStyle: (Float?, Float?, Float?, Boolean?, SubtitleAnimation?) -> Unit,
  onSelectSubtitle: (String) -> Unit,
  onSeekTo: (Long) -> Unit,
  // Color & FX
  onUpdateColorGrading: (Float?, Float?, Float?, Float?, Float?, FilterPreset?) -> Unit,
  onResetColorGrading: () -> Unit,
  onAddFxTrackLut: (FilterPreset) -> Unit,
  onUpdateTransition: (TransitionType, Long?) -> Unit,
  // Transform & Keyframes
  onUpdateTransform: (Float?, Float?, Float?, Float?, Float?, Boolean?, Boolean?) -> Unit,
  onResetTransform: () -> Unit,
  onAddKeyframe: (EasingType) -> Unit,
  onDeleteKeyframe: (String) -> Unit,
  // Speed & Audio
  onUpdateSpeedAndVolume: (Float?, Float?, Long?, Long?) -> Unit,
  onReverseClip: () -> Unit,
  onFreezeClip: () -> Unit,
  onExtractAudio: () -> Unit,
  onCloseDrawer: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(
        if (onCloseDrawer != null) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        else RoundedCornerShape(0.dp)
      )
      .background(ReaCutSurface)
      .border(
        width = 1.dp,
        color = if (onCloseDrawer != null) ReaCutCyan.copy(alpha = 0.5f) else ReaCutBorder,
        shape = if (onCloseDrawer != null) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        else RoundedCornerShape(0.dp),
      )
      .navigationBarsPadding(),
  ) {
    // Top Tab Strip + Optional Close Drawer Button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(ReaCutBgDarkest)
        .border(0.5.dp, ReaCutBorder)
        .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        DockTabItem(
          tab = StudioDockTab.MEDIA,
          icon = Icons.Default.VideoLibrary,
          selected = state.activeDockTab == StudioDockTab.MEDIA,
          onClick = { onSelectTab(StudioDockTab.MEDIA) },
        )
        DockTabItem(
          tab = StudioDockTab.SUBTITLES,
          icon = Icons.Default.ClosedCaption,
          selected = state.activeDockTab == StudioDockTab.SUBTITLES,
          onClick = { onSelectTab(StudioDockTab.SUBTITLES) },
        )
        DockTabItem(
          tab = StudioDockTab.COLOR_FX,
          icon = Icons.Default.ColorLens,
          selected = state.activeDockTab == StudioDockTab.COLOR_FX,
          onClick = { onSelectTab(StudioDockTab.COLOR_FX) },
        )
        DockTabItem(
          tab = StudioDockTab.TRANSFORM,
          icon = Icons.Default.Transform,
          selected = state.activeDockTab == StudioDockTab.TRANSFORM,
          onClick = { onSelectTab(StudioDockTab.TRANSFORM) },
        )
        DockTabItem(
          tab = StudioDockTab.SPEED_AUDIO,
          icon = Icons.Default.Speed,
          selected = state.activeDockTab == StudioDockTab.SPEED_AUDIO,
          onClick = { onSelectTab(StudioDockTab.SPEED_AUDIO) },
        )
      }

      if (onCloseDrawer != null) {
        Spacer(modifier = Modifier.width(6.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ReaCutSurfaceElevated)
            .border(1.dp, ReaCutPlayheadCoral.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .clickable(onClick = onCloseDrawer)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("close_inspector_drawer_button"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "✕ Hide",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ReaCutPlayheadCoral,
          )
        }
      }
    }

    // Active Panel Content
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      when (state.activeDockTab) {
        StudioDockTab.MEDIA -> MediaAndDraftsPanel(
          state = state,
          onPickDeviceVideo = onPickDeviceVideo,
          onPickDeviceAudio = onPickDeviceAudio,
          onOpenCapCutDraftModal = onOpenCapCutDraftModal,
          onAddProceduralClip = onAddProceduralClip,
          onUpdateProjectConfig = onUpdateProjectConfig,
        )
        StudioDockTab.SUBTITLES -> SubtitlesAndSrtPanel(
          state = state,
          onPickSrtFile = onPickSrtFile,
          onAddSubtitleAtPlayhead = onAddSubtitleAtPlayhead,
          onUpdateSubtitleCue = onUpdateSubtitleCue,
          onDeleteSubtitleCue = onDeleteSubtitleCue,
          onShiftAllSubtitles = onShiftAllSubtitles,
          onAutoGenerateSubtitles = onAutoGenerateSubtitles,
          onUpdateGlobalSubtitleStyle = onUpdateGlobalSubtitleStyle,
          onSelectSubtitle = onSelectSubtitle,
          onSeekTo = onSeekTo,
        )
        StudioDockTab.COLOR_FX -> ColorAndLutPanel(
          state = state,
          onUpdateColorGrading = onUpdateColorGrading,
          onResetColorGrading = onResetColorGrading,
          onAddFxTrackLut = onAddFxTrackLut,
          onUpdateTransition = onUpdateTransition,
        )
        StudioDockTab.TRANSFORM -> TransformAndKeyframesPanel(
          state = state,
          onUpdateTransform = onUpdateTransform,
          onResetTransform = onResetTransform,
          onAddKeyframe = onAddKeyframe,
          onDeleteKeyframe = onDeleteKeyframe,
          onSeekTo = onSeekTo,
        )
        StudioDockTab.SPEED_AUDIO -> SpeedAndAudioPanel(
          state = state,
          onUpdateSpeedAndVolume = onUpdateSpeedAndVolume,
          onReverseClip = onReverseClip,
          onFreezeClip = onFreezeClip,
          onExtractAudio = onExtractAudio,
        )
      }
    }
  }
}

@Composable
private fun DockTabItem(
  tab: StudioDockTab,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(if (selected) ReaCutCyan.copy(alpha = 0.18f) else Color.Transparent)
      .border(
        width = 1.dp,
        color = if (selected) ReaCutCyan else ReaCutBorder,
        shape = RoundedCornerShape(6.dp),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag("dock_tab_${tab.name.lowercase()}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = tab.title,
      tint = if (selected) ReaCutCyan else ReaCutTextSecondary,
      modifier = Modifier.size(15.dp),
    )
    Text(
      text = tab.title,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      ),
      color = if (selected) ReaCutCyan else ReaCutTextSecondary,
    )
  }
}

@Composable
private fun MediaAndDraftsPanel(
  state: EditorUiState,
  onPickDeviceVideo: () -> Unit,
  onPickDeviceAudio: () -> Unit,
  onOpenCapCutDraftModal: () -> Unit,
  onAddProceduralClip: (ProceduralClipTheme, TrackType) -> Unit,
  onUpdateProjectConfig: (AspectRatioPreset?, ResolutionPreset?, Int?) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Row 1: Device Import & CapCut Draft Import Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Button(
        onClick = onPickDeviceVideo,
        colors = ButtonDefaults.buttonColors(containerColor = ReaCutSurfaceElevated, contentColor = ReaCutCyan),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .testTag("import_device_video_button"),
      ) {
        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("+ Device Video", style = MaterialTheme.typography.labelSmall, maxLines = 1)
      }

      Button(
        onClick = onPickDeviceAudio,
        colors = ButtonDefaults.buttonColors(containerColor = ReaCutSurfaceElevated, contentColor = ReaCutTeal),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .testTag("import_device_audio_button"),
      ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("+ Device Audio", style = MaterialTheme.typography.labelSmall, maxLines = 1)
      }

      Button(
        onClick = onOpenCapCutDraftModal,
        colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .weight(1.1f)
          .height(36.dp)
          .testTag("open_capcut_draft_importer_button"),
      ) {
        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("CapCut Draft", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
      }
    }

    // Studio Procedural Video Generators (Add to V1 or V2 PIP)
    Text(
      text = "Built-In Studio Generator Clips (Tap +V1 or +PIP)",
      style = MaterialTheme.typography.labelSmall,
      color = ReaCutTextSecondary,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ProceduralClipTheme.entries.forEach { theme ->
        Column(
          modifier = Modifier
            .width(138.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ReaCutSurfaceElevated)
            .border(1.dp, Color(theme.primaryHex).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = theme.title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(theme.primaryHex),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReaCutBgPrimary)
                .clickable { onAddProceduralClip(theme, TrackType.VIDEO) }
                .padding(vertical = 4.dp)
                .testTag("add_v1_${theme.name.lowercase()}"),
              contentAlignment = Alignment.Center,
            ) {
              Text("+ V1", style = MaterialTheme.typography.labelSmall, color = ReaCutCyan)
            }
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReaCutBgPrimary)
                .clickable { onAddProceduralClip(theme, TrackType.OVERLAY) }
                .padding(vertical = 4.dp)
                .testTag("add_pip_${theme.name.lowercase()}"),
              contentAlignment = Alignment.Center,
            ) {
              Text("+ PIP", style = MaterialTheme.typography.labelSmall, color = ReaCutTeal)
            }
          }
        }
      }
    }

    // Canvas Aspect Ratio & Frame Rate Config
    Text(
      text = "Canvas Aspect Ratio & Frame Rate",
      style = MaterialTheme.typography.labelSmall,
      color = ReaCutTextSecondary,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      AspectRatioPreset.entries.forEach { ratio ->
        val isSel = state.project.config.aspectRatio == ratio
        ChipOption(
          label = ratio.label,
          selected = isSel,
          tag = "ratio_${ratio.ratioWidth}_${ratio.ratioHeight}",
          onClick = { onUpdateProjectConfig(ratio, null, null) },
        )
      }
      listOf(24, 30, 60).forEach { fps ->
        val isSel = state.project.config.fps == fps
        ChipOption(
          label = "${fps} FPS",
          selected = isSel,
          tag = "fps_$fps",
          onClick = { onUpdateProjectConfig(null, null, fps) },
        )
      }
    }
  }
}

@Composable
private fun SubtitlesAndSrtPanel(
  state: EditorUiState,
  onPickSrtFile: () -> Unit,
  onAddSubtitleAtPlayhead: (String, String) -> Unit,
  onUpdateSubtitleCue: (String, String, String, Long, Long) -> Unit,
  onDeleteSubtitleCue: (String) -> Unit,
  onShiftAllSubtitles: (Long) -> Unit,
  onAutoGenerateSubtitles: () -> Unit,
  onUpdateGlobalSubtitleStyle: (Float?, Float?, Float?, Boolean?, SubtitleAnimation?) -> Unit,
  onSelectSubtitle: (String) -> Unit,
  onSeekTo: (Long) -> Unit,
) {
  val selectedCue = state.selectedSubtitle
  var editPrimary by remember(selectedCue?.id, selectedCue?.text) {
    mutableStateOf(selectedCue?.text ?: "")
  }
  var editSecondary by remember(selectedCue?.id, selectedCue?.secondaryText) {
    mutableStateOf(selectedCue?.secondaryText ?: "")
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Top Subtitle Action Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      ChipOption(
        label = "+ Add Cue at Playhead",
        selected = true,
        tag = "subtitle_add_cue",
        onClick = { onAddSubtitleAtPlayhead("New ReaCut Caption", "ترجمة جديدة") },
      )
      ChipOption(
        label = "Auto-Generate Captions",
        selected = false,
        tag = "subtitle_auto_generate",
        onClick = onAutoGenerateSubtitles,
      )
      ChipOption(
        label = "Import .SRT / .VTT",
        selected = false,
        tag = "subtitle_import_srt",
        onClick = onPickSrtFile,
      )
      ChipOption(
        label = "Shift -200ms",
        selected = false,
        tag = "subtitle_shift_minus",
        onClick = { onShiftAllSubtitles(-200L) },
      )
      ChipOption(
        label = "Shift +200ms",
        selected = false,
        tag = "subtitle_shift_plus",
        onClick = { onShiftAllSubtitles(200L) },
      )
    }

    // Active Subtitle Editor
    if (selectedCue != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = editPrimary,
          onValueChange = {
            editPrimary = it
            onUpdateSubtitleCue(selectedCue.id, it, editSecondary, selectedCue.startTimeMs, selectedCue.endTimeMs)
          },
          label = { Text("Primary Subtitle", style = MaterialTheme.typography.labelSmall) },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("subtitle_primary_input"),
        )
        OutlinedTextField(
          value = editSecondary,
          onValueChange = {
            editSecondary = it
            onUpdateSubtitleCue(selectedCue.id, editPrimary, it, selectedCue.startTimeMs, selectedCue.endTimeMs)
          },
          label = { Text("Bilingual / Line 2", style = MaterialTheme.typography.labelSmall) },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("subtitle_secondary_input"),
        )
        IconButton(
          onClick = { onDeleteSubtitleCue(selectedCue.id) },
          modifier = Modifier.testTag("subtitle_delete_cue"),
        ) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Subtitle", tint = ReaCutPlayheadCoral)
        }
      }
    }

    // Subtitle Cues Horizontal Scroller
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      state.project.subtitles.forEachIndexed { idx, cue ->
        val isSel = cue.id == selectedCue?.id
        Column(
          modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSel) ReaCutSurfaceElevated else ReaCutBgPrimary)
            .border(1.dp, if (isSel) ReaCutCyan else ReaCutBorder, RoundedCornerShape(6.dp))
            .clickable {
              onSelectSubtitle(cue.id)
              onSeekTo(cue.startTimeMs + 50L)
            }
            .padding(6.dp)
            .testTag("subtitle_cue_card_$idx"),
        ) {
          Text(
            text = "#${idx + 1} ${SrtSubtitleEngineFormatShort(cue.startTimeMs)} → ${SrtSubtitleEngineFormatShort(cue.endTimeMs)}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = ReaCutAmber,
          )
          Text(
            text = cue.text,
            style = MaterialTheme.typography.labelSmall,
            color = ReaCutTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Global Subtitle Styling Sliders
    val style = selectedCue?.style ?: state.project.subtitles.firstOrNull()?.style
    if (style != null) {
      CompactSliderRow(
        label = "Font Size",
        valueText = "${style.fontSizeSp.roundToInt()} sp",
        value = style.fontSizeSp,
        valueRange = 12f..36f,
        onValueChange = { onUpdateGlobalSubtitleStyle(it, null, null, null, null) },
      )
      CompactSliderRow(
        label = "Vertical Pos",
        valueText = "${(style.verticalPosition * 100).roundToInt()}%",
        value = style.verticalPosition,
        valueRange = 0.15f..0.92f,
        onValueChange = { onUpdateGlobalSubtitleStyle(null, null, it, null, null) },
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ChipOption(
          label = if (style.showBilingual) "Bilingual: ON" else "Bilingual: OFF",
          selected = style.showBilingual,
          tag = "subtitle_bilingual_toggle",
          onClick = { onUpdateGlobalSubtitleStyle(null, null, null, !style.showBilingual, null) },
        )
        SubtitleAnimation.entries.forEach { anim ->
          ChipOption(
            label = "Anim: ${anim.displayName}",
            selected = style.animation == anim,
            tag = "subtitle_anim_${anim.name.lowercase()}",
            onClick = { onUpdateGlobalSubtitleStyle(null, null, null, null, anim) },
          )
        }
      }
    }
  }
}

@Composable
private fun ColorAndLutPanel(
  state: EditorUiState,
  onUpdateColorGrading: (Float?, Float?, Float?, Float?, Float?, FilterPreset?) -> Unit,
  onResetColorGrading: () -> Unit,
  onAddFxTrackLut: (FilterPreset) -> Unit,
  onUpdateTransition: (TransitionType, Long?) -> Unit,
) {
  val clip = state.selectedClip
  val cg = clip?.colorGrading

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Studio LUT Presets (Tap for Clip • Long/FX to drop on FX Track)",
        style = MaterialTheme.typography.labelSmall,
        color = ReaCutTextSecondary,
      )
      Text(
        text = "Reset Grade",
        style = MaterialTheme.typography.labelSmall,
        color = ReaCutCyan,
        modifier = Modifier
          .clickable { onResetColorGrading() }
          .padding(4.dp)
          .testTag("reset_color_grade_button"),
      )
    }

    // 12 LUT Filter Presets
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterPreset.entries.forEach { preset ->
        val isSel = cg?.filterPreset == preset
        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSel) ReaCutCyan.copy(alpha = 0.2f) else ReaCutSurfaceElevated)
            .border(1.dp, if (isSel) ReaCutCyan else ReaCutBorder, RoundedCornerShape(6.dp))
            .clickable { onUpdateColorGrading(null, null, null, null, null, preset) }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("lut_preset_${preset.code}"),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSel) ReaCutCyan else ReaCutTextPrimary,
          )
          if (preset != FilterPreset.NONE) {
            Text(
              text = "+FX Track",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = ReaCutTeal,
              modifier = Modifier
                .padding(top = 2.dp)
                .clickable { onAddFxTrackLut(preset) },
            )
          }
        }
      }
    }

    // Transition Selector
    Text(
      text = "Clip Entry Transition",
      style = MaterialTheme.typography.labelSmall,
      color = ReaCutTextSecondary,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TransitionType.entries.forEach { tr ->
        ChipOption(
          label = tr.displayName,
          selected = clip?.transition?.type == tr,
          tag = "transition_${tr.capCutKey}",
          onClick = { onUpdateTransition(tr, null) },
        )
      }
    }

    // 5 Color Grading Sliders
    if (cg != null) {
      CompactSliderRow(
        label = "Brightness",
        valueText = String.format("%+.2f", cg.brightness),
        value = cg.brightness,
        valueRange = -0.45f..0.45f,
        onValueChange = { onUpdateColorGrading(it, null, null, null, null, null) },
      )
      CompactSliderRow(
        label = "Contrast",
        valueText = String.format("%.2fx", cg.contrast),
        value = cg.contrast,
        valueRange = 0.4f..2.2f,
        onValueChange = { onUpdateColorGrading(null, it, null, null, null, null) },
      )
      CompactSliderRow(
        label = "Saturation",
        valueText = String.format("%.2fx", cg.saturation),
        value = cg.saturation,
        valueRange = 0f..2.5f,
        onValueChange = { onUpdateColorGrading(null, null, it, null, null, null) },
      )
      CompactSliderRow(
        label = "Warmth",
        valueText = String.format("%+.2f", cg.temperature),
        value = cg.temperature,
        valueRange = -0.6f..0.6f,
        onValueChange = { onUpdateColorGrading(null, null, null, it, null, null) },
      )
      CompactSliderRow(
        label = "Vignette",
        valueText = "${(cg.vignette * 100).roundToInt()}%",
        value = cg.vignette,
        valueRange = 0f..1f,
        onValueChange = { onUpdateColorGrading(null, null, null, null, it, null) },
      )
    }
  }
}

@Composable
private fun TransformAndKeyframesPanel(
  state: EditorUiState,
  onUpdateTransform: (Float?, Float?, Float?, Float?, Float?, Boolean?, Boolean?) -> Unit,
  onResetTransform: () -> Unit,
  onAddKeyframe: (EasingType) -> Unit,
  onDeleteKeyframe: (String) -> Unit,
  onSeekTo: (Long) -> Unit,
) {
  val clip = state.selectedClip
  var selectedEasing by remember { mutableStateOf(EasingType.EASE_IN_OUT) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (clip == null) {
      Text("Select a clip on the timeline to edit Transform & Keyframes.", color = ReaCutTextSecondary)
      return@Column
    }

    // Keyframe Header Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      ChipOption(
        label = "+ Add/Update Keyframe ♦",
        selected = true,
        tag = "inspector_add_keyframe",
        onClick = { onAddKeyframe(selectedEasing) },
      )
      EasingType.entries.forEach { easing ->
        ChipOption(
          label = easing.displayName,
          selected = selectedEasing == easing,
          tag = "easing_${easing.name.lowercase()}",
          onClick = { selectedEasing = easing },
        )
      }
      ChipOption(
        label = "Flip H",
        selected = clip.mirrorHorizontal,
        tag = "transform_flip_h",
        onClick = { onUpdateTransform(null, null, null, null, null, !clip.mirrorHorizontal, null) },
      )
      ChipOption(
        label = "Flip V",
        selected = clip.mirrorVertical,
        tag = "transform_flip_v",
        onClick = { onUpdateTransform(null, null, null, null, null, null, !clip.mirrorVertical) },
      )
      ChipOption(
        label = "Reset",
        selected = false,
        tag = "transform_reset",
        onClick = onResetTransform,
      )
    }

    // Existing Keyframes List
    if (clip.keyframes.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        clip.keyframes.forEachIndexed { idx, kf ->
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(ReaCutBgPrimary)
              .border(1.dp, ReaCutAmber, RoundedCornerShape(6.dp))
              .clickable { onSeekTo(clip.timelineStartMs + kf.offsetMs) }
              .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "♦ KF${idx + 1} (+${kf.offsetMs}ms • ${(kf.scale * 100).roundToInt()}%)",
              style = MaterialTheme.typography.labelSmall,
              color = ReaCutAmber,
            )
            Text(
              text = "✕",
              style = MaterialTheme.typography.labelSmall,
              color = ReaCutPlayheadCoral,
              modifier = Modifier.clickable { onDeleteKeyframe(kf.id) },
            )
          }
        }
      }
    }

    CompactSliderRow(
      label = "Scale",
      valueText = "${(clip.scale * 100).roundToInt()}%",
      value = clip.scale,
      valueRange = 0.2f..3.0f,
      onValueChange = { onUpdateTransform(it, null, null, null, null, null, null) },
    )
    CompactSliderRow(
      label = "Position X",
      valueText = String.format("%+.2f", clip.positionX),
      value = clip.positionX,
      valueRange = -1.0f..1.0f,
      onValueChange = { onUpdateTransform(null, it, null, null, null, null, null) },
    )
    CompactSliderRow(
      label = "Position Y",
      valueText = String.format("%+.2f", clip.positionY),
      value = clip.positionY,
      valueRange = -1.0f..1.0f,
      onValueChange = { onUpdateTransform(null, null, it, null, null, null, null) },
    )
    CompactSliderRow(
      label = "Rotation",
      valueText = "${clip.rotationDeg.roundToInt()}°",
      value = clip.rotationDeg,
      valueRange = -180f..180f,
      onValueChange = { onUpdateTransform(null, null, null, it, null, null, null) },
    )
    CompactSliderRow(
      label = "Opacity",
      valueText = "${(clip.opacity * 100).roundToInt()}%",
      value = clip.opacity,
      valueRange = 0.05f..1.0f,
      onValueChange = { onUpdateTransform(null, null, null, null, it, null, null) },
    )
  }
}

@Composable
private fun SpeedAndAudioPanel(
  state: EditorUiState,
  onUpdateSpeedAndVolume: (Float?, Float?, Long?, Long?) -> Unit,
  onReverseClip: () -> Unit,
  onFreezeClip: () -> Unit,
  onExtractAudio: () -> Unit,
) {
  val clip = state.selectedClip
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (clip == null) {
      Text("Select a clip on the timeline to adjust Speed & Audio.", color = ReaCutTextSecondary)
      return@Column
    }

    // Speed Preset Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { spd ->
        ChipOption(
          label = "${spd}x",
          selected = kotlin.math.abs(clip.speed - spd) < 0.03f,
          tag = "speed_preset_$spd",
          onClick = { onUpdateSpeedAndVolume(spd, null, null, null) },
        )
      }
      ChipOption(
        label = if (clip.isReversed) "Reversed ✓" else "Reverse Clip",
        selected = clip.isReversed,
        tag = "speed_reverse_toggle",
        onClick = onReverseClip,
      )
      ChipOption(
        label = "Freeze 1.5s",
        selected = clip.isFrozen,
        tag = "speed_freeze_button",
        onClick = onFreezeClip,
      )
      ChipOption(
        label = "Extract to A1",
        selected = false,
        tag = "speed_extract_audio",
        onClick = onExtractAudio,
      )
    }

    CompactSliderRow(
      label = "Speed",
      valueText = String.format("%.2fx", clip.speed),
      value = clip.speed,
      valueRange = 0.25f..4.0f,
      onValueChange = { onUpdateSpeedAndVolume(it, null, null, null) },
    )
    CompactSliderRow(
      label = "Volume",
      valueText = "${(clip.volume * 100).roundToInt()}%",
      value = clip.volume,
      valueRange = 0f..2.0f,
      onValueChange = { onUpdateSpeedAndVolume(null, it, null, null) },
    )
    CompactSliderRow(
      label = "Fade In",
      valueText = "${clip.fadeInMs} ms",
      value = clip.fadeInMs.toFloat(),
      valueRange = 0f..2000f,
      onValueChange = { onUpdateSpeedAndVolume(null, null, it.roundToLong(), null) },
    )
    CompactSliderRow(
      label = "Fade Out",
      valueText = "${clip.fadeOutMs} ms",
      value = clip.fadeOutMs.toFloat(),
      valueRange = 0f..2000f,
      onValueChange = { onUpdateSpeedAndVolume(null, null, null, it.roundToLong()) },
    )
  }
}

@Composable
private fun CompactSliderRow(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(28.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = ReaCutTextSecondary,
      modifier = Modifier.width(78.dp),
    )
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors = SliderDefaults.colors(
        thumbColor = ReaCutCyan,
        activeTrackColor = ReaCutCyan,
        inactiveTrackColor = ReaCutBorder,
      ),
      modifier = Modifier.weight(1f),
    )
    Text(
      text = valueText,
      style = MaterialTheme.typography.labelSmall,
      color = ReaCutCyan,
      modifier = Modifier.width(56.dp),
    )
  }
}

@Composable
fun ChipOption(
  label: String,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(if (selected) ReaCutCyan.copy(alpha = 0.2f) else ReaCutSurfaceElevated)
      .border(1.dp, if (selected) ReaCutCyan else ReaCutBorder, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 5.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (selected) ReaCutCyan else ReaCutTextPrimary,
    )
  }
}

private fun SrtSubtitleEngineFormatShort(ms: Long): String {
  val sec = ms / 1000L
  val rem = (ms % 1000L) / 100L
  return String.format("%02d.%d", sec, rem)
}
