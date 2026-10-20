package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.TrackType
import com.example.editor.EditorViewModel
import com.example.ui.components.CapCutDraftImporterModal
import com.example.ui.components.CompactMobileActionStrip
import com.example.ui.components.ExportStudioModal
import com.example.ui.components.InspectorAndToolsDock
import com.example.ui.components.LicenseAndArchitectureModal
import com.example.ui.components.MobileSideShortcutRail
import com.example.ui.components.MultiTrackTimeline
import com.example.ui.components.PreviewMonitor
import com.example.ui.components.ProjectManagerModal
import com.example.ui.components.QuickActionToolbar
import com.example.ui.components.SlideOutQuickToolsSheet
import com.example.ui.components.TopStudioBar
import com.example.ui.theme.ReaCutBgPrimary

@Composable
fun ReaCutStudioScreen(
  viewModel: EditorViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Zero-permission Android Photo Picker for importing local videos
  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      viewModel.importDeviceMediaUri(context, uri, TrackType.VIDEO)
    }
  }

  // Document picker for audio files
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      viewModel.importDeviceMediaUri(context, uri, TrackType.AUDIO)
    }
  }

  // Document picker for .srt / .vtt subtitle files
  val srtPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      }.getOrNull()?.let { content ->
        viewModel.importSrtContent(content)
      }
    }
  }

  // Document picker for CapCut draft_content.json or .reacut.json
  val draftJsonPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      }.getOrNull()?.let { jsonStr ->
        viewModel.importCapCutDraftJson(jsonStr)
      }
    }
  }

  BackHandler(
    enabled = state.showExportModal ||
      state.showCapCutDraftModal ||
      state.showProjectDrawer ||
      state.showLicenseArchitectureModal ||
      state.isInspectorDrawerOpen ||
      state.showQuickToolsSheet ||
      state.isPlaying,
  ) {
    when {
      state.showExportModal -> viewModel.setShowExportModal(false)
      state.showCapCutDraftModal -> viewModel.setShowCapCutDraftModal(false)
      state.showProjectDrawer -> viewModel.setShowProjectDrawer(false)
      state.showLicenseArchitectureModal -> viewModel.setShowLicenseArchitectureModal(false)
      state.isInspectorDrawerOpen || state.showQuickToolsSheet -> viewModel.closeInspectorDrawer()
      state.isPlaying -> viewModel.pause()
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(ReaCutBgPrimary)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    val isExpandedLandscape = maxWidth >= 680.dp

    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        // 1. Compact Top Studio Header Bar
        TopStudioBar(
          state = state,
          onOpenProjects = { viewModel.setShowProjectDrawer(true) },
          onOpenCapCutDraftModal = { viewModel.setShowCapCutDraftModal(true) },
          onOpenLicenseModal = { viewModel.setShowLicenseArchitectureModal(true) },
          onOpenExportModal = { viewModel.setShowExportModal(true) },
          onUndo = viewModel::undo,
          onRedo = viewModel::redo,
        )

        if (isExpandedLandscape) {
          // Landscape / Tablet Layout: Full Preview + Multi-Track Timeline with Side Shortcut Rail
          Row(
            modifier = Modifier
              .weight(0.55f)
              .fillMaxWidth(),
          ) {
            PreviewMonitor(
              state = state,
              onTogglePlayPause = viewModel::togglePlayPause,
              onStepBackward = { viewModel.stepFrames(-1) },
              onStepForward = { viewModel.stepFrames(1) },
              onJumpStart = { viewModel.seekTo(0L) },
              onJumpEnd = { viewModel.seekTo(state.project.totalDurationMs) },
              onToggleLoop = viewModel::toggleLoop,
              onToggleSafeGuides = viewModel::toggleSafeGuides,
              modifier = Modifier
                .weight(if (state.isInspectorDrawerOpen) 0.52f else 1f)
                .fillMaxHeight(),
            )

            AnimatedVisibility(
              visible = state.isInspectorDrawerOpen,
              modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
            ) {
              InspectorAndToolsDock(
                state = state,
                onSelectTab = viewModel::openDockTab,
                onPickDeviceVideo = {
                  videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onPickDeviceAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                onPickSrtFile = { srtPickerLauncher.launch(arrayOf("*/*")) },
                onOpenCapCutDraftModal = { viewModel.setShowCapCutDraftModal(true) },
                onAddProceduralClip = viewModel::addProceduralStudioClip,
                onUpdateProjectConfig = { ratio, res, fps ->
                  viewModel.updateProjectConfig(aspectRatio = ratio, resolution = res, fps = fps)
                },
                onAddSubtitleAtPlayhead = viewModel::addSubtitleCueAtPlayhead,
                onUpdateSubtitleCue = viewModel::updateSubtitleCue,
                onDeleteSubtitleCue = viewModel::deleteSubtitleCue,
                onShiftAllSubtitles = viewModel::shiftAllSubtitles,
                onAutoGenerateSubtitles = viewModel::autoGenerateSubtitlesFromTimeline,
                onUpdateGlobalSubtitleStyle = { size, bg, pos, bi, anim ->
                  viewModel.updateGlobalSubtitleStyle(
                    fontSizeSp = size,
                    bgOpacity = bg,
                    verticalPosition = pos,
                    showBilingual = bi,
                    animation = anim,
                  )
                },
                onSelectSubtitle = viewModel::selectSubtitle,
                onSeekTo = viewModel::seekTo,
                onUpdateColorGrading = viewModel::updateSelectedClipColorGrading,
                onResetColorGrading = viewModel::resetSelectedClipColorGrading,
                onAddFxTrackLut = viewModel::addFxTrackLutClip,
                onUpdateTransition = viewModel::updateSelectedClipTransition,
                onUpdateTransform = viewModel::updateSelectedClipTransform,
                onResetTransform = viewModel::resetSelectedClipTransform,
                onAddKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
                onDeleteKeyframe = viewModel::deleteKeyframe,
                onUpdateSpeedAndVolume = viewModel::updateSelectedClipSpeedAndVolume,
                onReverseClip = viewModel::reverseSelectedClip,
                onFreezeClip = viewModel::freezeFrameAtPlayhead,
                onExtractAudio = viewModel::extractAudioToTrack,
                onCloseDrawer = viewModel::closeInspectorDrawer,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }

          QuickActionToolbar(
            state = state,
            onSplit = viewModel::splitClipAtPlayhead,
            onTrimStart = viewModel::trimClipStartToPlayhead,
            onTrimEnd = viewModel::trimClipEndToPlayhead,
            onDuplicate = viewModel::duplicateSelectedClip,
            onDelete = viewModel::deleteSelectedClip,
            onAddKeyframe = { viewModel.addOrUpdateKeyframeAtPlayhead() },
            onFreeze = viewModel::freezeFrameAtPlayhead,
            onReverse = viewModel::reverseSelectedClip,
            onExtractAudio = viewModel::extractAudioToTrack,
            onAddMarker = { viewModel.addMarkerAtPlayhead() },
            onToggleSnap = viewModel::toggleSnap,
            onZoomIn = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec + 16f) },
            onZoomOut = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec - 16f) },
          )

          MultiTrackTimeline(
            state = state,
            onSeekTo = viewModel::seekTo,
            onSelectClip = viewModel::selectClip,
            onToggleTrackMute = viewModel::toggleTrackMute,
            onToggleTrackLock = viewModel::toggleTrackLock,
            onToggleTrackHidden = viewModel::toggleTrackHidden,
            onAdjustClipTrim = viewModel::adjustSelectedClipTiming,
            modifier = Modifier
              .weight(0.45f)
              .fillMaxWidth(),
          )
        } else {
          // Mobile Phone Uncluttered Layout:
          // Spacious Preview Monitor (48%) + Slim Essential Bar + Spacious Multi-Track Timeline (52%).
          // All extra panels (Media, Subtitles, Color/LUTs, Transform/Keyframes, Audio/Speed, Cut Tools)
          // are opened on-demand via the Floating Side Shortcut Rail!
          PreviewMonitor(
            state = state,
            onTogglePlayPause = viewModel::togglePlayPause,
            onStepBackward = { viewModel.stepFrames(-1) },
            onStepForward = { viewModel.stepFrames(1) },
            onJumpStart = { viewModel.seekTo(0L) },
            onJumpEnd = { viewModel.seekTo(state.project.totalDurationMs) },
            onToggleLoop = viewModel::toggleLoop,
            onToggleSafeGuides = viewModel::toggleSafeGuides,
            modifier = Modifier
              .weight(0.47f)
              .fillMaxWidth(),
          )

          CompactMobileActionStrip(
            state = state,
            onSplit = viewModel::splitClipAtPlayhead,
            onAddKeyframe = { viewModel.addOrUpdateKeyframeAtPlayhead() },
            onDuplicate = viewModel::duplicateSelectedClip,
            onDelete = viewModel::deleteSelectedClip,
            onZoomIn = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec + 16f) },
            onZoomOut = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec - 16f) },
            onOpenAllCutTools = viewModel::toggleQuickToolsSheet,
          )

          MultiTrackTimeline(
            state = state,
            onSeekTo = viewModel::seekTo,
            onSelectClip = viewModel::selectClip,
            onToggleTrackMute = viewModel::toggleTrackMute,
            onToggleTrackLock = viewModel::toggleTrackLock,
            onToggleTrackHidden = viewModel::toggleTrackHidden,
            onAdjustClipTrim = viewModel::adjustSelectedClipTiming,
            modifier = Modifier
              .weight(0.53f)
              .fillMaxWidth(),
          )
        }
      }

      // Floating Collapsible Side Shortcut Rail on the right edge of the screen
      MobileSideShortcutRail(
        state = state,
        onToggleSideRail = viewModel::toggleSideRail,
        onSelectDockTab = viewModel::selectDockTab,
        onToggleQuickToolsSheet = viewModel::toggleQuickToolsSheet,
        onOpenCapCutDraftModal = { viewModel.setShowCapCutDraftModal(true) },
        onOpenProjects = { viewModel.setShowProjectDrawer(true) },
        onOpenLicenseModal = { viewModel.setShowLicenseArchitectureModal(true) },
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = 42.dp, bottom = 16.dp),
      )

      // Slide-Out Bottom Overlay Drawer for Inspector Panels on Mobile (keeps Preview Monitor visible at top!)
      if (!isExpandedLandscape) {
        AnimatedVisibility(
          visible = state.isInspectorDrawerOpen,
          enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
          exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.48f),
        ) {
          InspectorAndToolsDock(
            state = state,
            onSelectTab = viewModel::openDockTab,
            onPickDeviceVideo = {
              videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onPickDeviceAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
            onPickSrtFile = { srtPickerLauncher.launch(arrayOf("*/*")) },
            onOpenCapCutDraftModal = { viewModel.setShowCapCutDraftModal(true) },
            onAddProceduralClip = viewModel::addProceduralStudioClip,
            onUpdateProjectConfig = { ratio, res, fps ->
              viewModel.updateProjectConfig(aspectRatio = ratio, resolution = res, fps = fps)
            },
            onAddSubtitleAtPlayhead = viewModel::addSubtitleCueAtPlayhead,
            onUpdateSubtitleCue = viewModel::updateSubtitleCue,
            onDeleteSubtitleCue = viewModel::deleteSubtitleCue,
            onShiftAllSubtitles = viewModel::shiftAllSubtitles,
            onAutoGenerateSubtitles = viewModel::autoGenerateSubtitlesFromTimeline,
            onUpdateGlobalSubtitleStyle = { size, bg, pos, bi, anim ->
              viewModel.updateGlobalSubtitleStyle(
                fontSizeSp = size,
                bgOpacity = bg,
                verticalPosition = pos,
                showBilingual = bi,
                animation = anim,
              )
            },
            onSelectSubtitle = viewModel::selectSubtitle,
            onSeekTo = viewModel::seekTo,
            onUpdateColorGrading = viewModel::updateSelectedClipColorGrading,
            onResetColorGrading = viewModel::resetSelectedClipColorGrading,
            onAddFxTrackLut = viewModel::addFxTrackLutClip,
            onUpdateTransition = viewModel::updateSelectedClipTransition,
            onUpdateTransform = viewModel::updateSelectedClipTransform,
            onResetTransform = viewModel::resetSelectedClipTransform,
            onAddKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
            onDeleteKeyframe = viewModel::deleteKeyframe,
            onUpdateSpeedAndVolume = viewModel::updateSelectedClipSpeedAndVolume,
            onReverseClip = viewModel::reverseSelectedClip,
            onFreezeClip = viewModel::freezeFrameAtPlayhead,
            onExtractAudio = viewModel::extractAudioToTrack,
            onCloseDrawer = viewModel::closeInspectorDrawer,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // Slide-Out Quick Editing Tools Sheet (Trim In/Out, Freeze, Reverse, Extract Audio, Marker, Snap)
      AnimatedVisibility(
        visible = state.showQuickToolsSheet,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth(),
      ) {
        SlideOutQuickToolsSheet(
          state = state,
          onSplit = viewModel::splitClipAtPlayhead,
          onTrimStart = viewModel::trimClipStartToPlayhead,
          onTrimEnd = viewModel::trimClipEndToPlayhead,
          onDuplicate = viewModel::duplicateSelectedClip,
          onDelete = viewModel::deleteSelectedClip,
          onAddKeyframe = { viewModel.addOrUpdateKeyframeAtPlayhead() },
          onFreeze = viewModel::freezeFrameAtPlayhead,
          onReverse = viewModel::reverseSelectedClip,
          onExtractAudio = viewModel::extractAudioToTrack,
          onAddMarker = { viewModel.addMarkerAtPlayhead() },
          onToggleSnap = viewModel::toggleSnap,
          onZoomIn = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec + 16f) },
          onZoomOut = { viewModel.setTimelineZoom(state.timelineZoomPxPerSec - 16f) },
          onClose = viewModel::closeInspectorDrawer,
        )
      }
    }

    // Modals
    if (state.showExportModal) {
      ExportStudioModal(
        state = state,
        onStartMp4Export = { res, fps, burnSubs ->
          viewModel.startHardwareMp4Export(context, res, fps, burnSubs)
        },
        onDismiss = { viewModel.setShowExportModal(false) },
      )
    }

    if (state.showCapCutDraftModal) {
      CapCutDraftImporterModal(
        state = state,
        onPickDraftFileFromDevice = { draftJsonPickerLauncher.launch(arrayOf("*/*")) },
        onImportDraftJson = { jsonStr ->
          viewModel.importCapCutDraftJson(jsonStr)
        },
        onDismiss = { viewModel.setShowCapCutDraftModal(false) },
      )
    }

    if (state.showProjectDrawer) {
      ProjectManagerModal(
        state = state,
        savedProjects = savedProjects,
        onCreateProject = viewModel::createNewProject,
        onLoadProject = viewModel::loadSavedProject,
        onDeleteProject = viewModel::deleteSavedProject,
        onDismiss = { viewModel.setShowProjectDrawer(false) },
      )
    }

    if (state.showLicenseArchitectureModal) {
      LicenseAndArchitectureModal(
        onDismiss = { viewModel.setShowLicenseArchitectureModal(false) },
      )
    }
  }
}
