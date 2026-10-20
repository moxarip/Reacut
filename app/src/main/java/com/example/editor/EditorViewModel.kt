package com.example.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AspectRatioPreset
import com.example.core.CapCutDraftEngine
import com.example.core.ClipKeyframe
import com.example.core.ColorGrading
import com.example.core.DraftImportReport
import com.example.core.EasingType
import com.example.core.ExportStatus
import com.example.core.FilterPreset
import com.example.core.KeyframeColorEngine
import com.example.core.ProceduralClipTheme
import com.example.core.ProjectConfig
import com.example.core.ReaCutProject
import com.example.core.RealMp4ExportEngine
import com.example.core.ResolutionPreset
import com.example.core.SrtSubtitleEngine
import com.example.core.SubtitleAnimation
import com.example.core.SubtitleCue
import com.example.core.SubtitleStyle
import com.example.core.TimelineClip
import com.example.core.TimelineMarker
import com.example.core.TimelineTrack
import com.example.core.TrackType
import com.example.core.TransitionConfig
import com.example.core.TransitionType
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class StudioDockTab(val title: String) {
  MEDIA("Media & Drafts"),
  SUBTITLES("Subtitles / SRT"),
  COLOR_FX("Color & LUTs"),
  TRANSFORM("Transform & Keyframes"),
  SPEED_AUDIO("Speed & Audio"),
}

data class HistorySnapshot(
  val actionLabel: String,
  val project: ReaCutProject,
)

data class EditorUiState(
  val project: ReaCutProject = CapCutDraftEngine.createDefaultShowcaseProject(),
  val playheadMs: Long = 0L,
  val isPlaying: Boolean = false,
  val isLooping: Boolean = true,
  val showSafeGuides: Boolean = false,
  val snapEnabled: Boolean = true,
  val timelineZoomPxPerSec: Float = 68f, // 28f .. 180f
  val selectedClipId: String? = "clip_v1_intro",
  val selectedSubtitleId: String? = "sub_1",
  val activeDockTab: StudioDockTab = StudioDockTab.MEDIA,
  val isInspectorDrawerOpen: Boolean = false,
  val isSideRailExpanded: Boolean = true,
  val showQuickToolsSheet: Boolean = false,
  val undoCount: Int = 0,
  val redoCount: Int = 0,
  val lastActionMessage: String = "ReaCut Studio Ready",
  val exportStatus: ExportStatus = ExportStatus.Idle,
  // Modals
  val showProjectDrawer: Boolean = false,
  val showExportModal: Boolean = false,
  val showCapCutDraftModal: Boolean = false,
  val showLicenseArchitectureModal: Boolean = false,
) {
  val selectedClip: TimelineClip?
    get() = project.tracks.flatMap { it.clips }.firstOrNull { it.id == selectedClipId }

  val selectedSubtitle: SubtitleCue?
    get() = project.subtitles.firstOrNull { it.id == selectedSubtitleId }
      ?: project.subtitles.firstOrNull { playheadMs in it.startTimeMs..it.endTimeMs }
}

class EditorViewModel(
  private val repository: ProjectRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  val savedProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val undoStack = ArrayDeque<HistorySnapshot>()
  private val redoStack = ArrayDeque<HistorySnapshot>()
  private var playbackJob: Job? = null
  private var exportJob: Job? = null

  init {
    viewModelScope.launch {
      val initial = repository.ensureSeeded()
      _uiState.update { state ->
        state.copy(
          project = initial,
          selectedClipId = initial.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips?.firstOrNull()?.id,
          selectedSubtitleId = initial.subtitles.firstOrNull()?.id,
        )
      }
    }
  }

  private fun commitProjectMutation(actionLabel: String, mutator: (ReaCutProject) -> ReaCutProject) {
    val currentProject = _uiState.value.project
    undoStack.addLast(HistorySnapshot(actionLabel, currentProject))
    if (undoStack.size > 50) {
      undoStack.removeFirst()
    }
    redoStack.clear()

    val updated = mutator(currentProject).let { proj ->
      proj.copy(config = proj.config.copy(updatedAt = System.currentTimeMillis()))
    }

    _uiState.update { state ->
      val validClipId = if (updated.tracks.flatMap { it.clips }.any { it.id == state.selectedClipId }) {
        state.selectedClipId
      } else {
        updated.tracks.flatMap { it.clips }.firstOrNull()?.id
      }
      state.copy(
        project = updated,
        selectedClipId = validClipId,
        undoCount = undoStack.size,
        redoCount = redoStack.size,
        lastActionMessage = actionLabel,
      )
    }

    viewModelScope.launch {
      repository.saveProject(updated)
    }
  }

  fun undo() {
    if (undoStack.isEmpty()) return
    val current = _uiState.value.project
    val snapshot = undoStack.removeLast()
    redoStack.addLast(HistorySnapshot(snapshot.actionLabel, current))
    _uiState.update {
      it.copy(
        project = snapshot.project,
        undoCount = undoStack.size,
        redoCount = redoStack.size,
        lastActionMessage = "Undo: ${snapshot.actionLabel}",
      )
    }
    viewModelScope.launch { repository.saveProject(snapshot.project) }
  }

  fun redo() {
    if (redoStack.isEmpty()) return
    val current = _uiState.value.project
    val snapshot = redoStack.removeLast()
    undoStack.addLast(HistorySnapshot(snapshot.actionLabel, current))
    _uiState.update {
      it.copy(
        project = snapshot.project,
        undoCount = undoStack.size,
        redoCount = redoStack.size,
        lastActionMessage = "Redo: ${snapshot.actionLabel}",
      )
    }
    viewModelScope.launch { repository.saveProject(snapshot.project) }
  }

  // ==================== PLAYBACK & TRANSPORT ====================

  fun togglePlayPause() {
    if (_uiState.value.isPlaying) {
      pause()
    } else {
      play()
    }
  }

  fun play() {
    if (_uiState.value.isPlaying) return
    val totalDur = _uiState.value.project.totalDurationMs
    if (_uiState.value.playheadMs >= totalDur) {
      _uiState.update { it.copy(playheadMs = 0L) }
    }
    _uiState.update { it.copy(isPlaying = true) }

    playbackJob?.cancel()
    playbackJob = viewModelScope.launch {
      var lastTick = System.currentTimeMillis()
      while (isActive && _uiState.value.isPlaying) {
        delay(16L)
        val now = System.currentTimeMillis()
        val delta = (now - lastTick).coerceIn(1L, 100L)
        lastTick = now

        val state = _uiState.value
        val maxDur = state.project.totalDurationMs
        val nextMs = state.playheadMs + delta
        if (nextMs >= maxDur) {
          if (state.isLooping) {
            _uiState.update { it.copy(playheadMs = 0L) }
          } else {
            _uiState.update { it.copy(playheadMs = maxDur, isPlaying = false) }
            break
          }
        } else {
          _uiState.update { it.copy(playheadMs = nextMs) }
        }
      }
    }
  }

  fun pause() {
    playbackJob?.cancel()
    _uiState.update { it.copy(isPlaying = false) }
  }

  fun seekTo(targetMs: Long) {
    val maxDur = _uiState.value.project.totalDurationMs
    var clamped = targetMs.coerceIn(0L, maxDur)
    if (_uiState.value.snapEnabled && !_uiState.value.isPlaying) {
      // Snap to markers or clip edges within 90ms
      val snapPoints = mutableListOf(0L, maxDur)
      _uiState.value.project.markers.forEach { snapPoints.add(it.timeMs) }
      _uiState.value.project.tracks.flatMap { it.clips }.forEach {
        snapPoints.add(it.timelineStartMs)
        snapPoints.add(it.timelineEndMs)
      }
      val nearest = snapPoints.minByOrNull { kotlin.math.abs(it - clamped) }
      if (nearest != null && kotlin.math.abs(nearest - clamped) <= 75L) {
        clamped = nearest
      }
    }
    _uiState.update { it.copy(playheadMs = clamped) }
  }

  fun stepFrames(deltaFrames: Int) {
    pause()
    val fps = _uiState.value.project.config.fps.coerceAtLeast(1)
    val frameMs = (1000L / fps).coerceAtLeast(16L)
    seekTo(_uiState.value.playheadMs + deltaFrames * frameMs)
  }

  fun toggleLoop() {
    _uiState.update { it.copy(isLooping = !it.isLooping) }
  }

  fun toggleSafeGuides() {
    _uiState.update { it.copy(showSafeGuides = !it.showSafeGuides) }
  }

  fun toggleSnap() {
    _uiState.update {
      val next = !it.snapEnabled
      it.copy(
        snapEnabled = next,
        lastActionMessage = if (next) "Timeline Snap Enabled" else "Timeline Snap Disabled",
      )
    }
  }

  fun setTimelineZoom(zoomPxPerSec: Float) {
    _uiState.update { it.copy(timelineZoomPxPerSec = zoomPxPerSec.coerceIn(28f, 180f)) }
  }

  fun selectClip(clipId: String?) {
    _uiState.update { it.copy(selectedClipId = clipId) }
  }

  fun selectSubtitle(subtitleId: String?) {
    _uiState.update { it.copy(selectedSubtitleId = subtitleId) }
  }

  fun selectDockTab(tab: StudioDockTab) {
    _uiState.update { state ->
      val shouldClose = state.isInspectorDrawerOpen && state.activeDockTab == tab
      state.copy(
        activeDockTab = tab,
        isInspectorDrawerOpen = !shouldClose,
        showQuickToolsSheet = false,
      )
    }
  }

  fun openDockTab(tab: StudioDockTab) {
    _uiState.update {
      it.copy(
        activeDockTab = tab,
        isInspectorDrawerOpen = true,
        showQuickToolsSheet = false,
      )
    }
  }

  fun closeInspectorDrawer() {
    _uiState.update {
      it.copy(
        isInspectorDrawerOpen = false,
        showQuickToolsSheet = false,
      )
    }
  }

  fun toggleSideRail() {
    _uiState.update { it.copy(isSideRailExpanded = !it.isSideRailExpanded) }
  }

  fun toggleQuickToolsSheet() {
    _uiState.update {
      it.copy(
        showQuickToolsSheet = !it.showQuickToolsSheet,
        isInspectorDrawerOpen = false,
      )
    }
  }

  // ==================== TIMELINE EDITING OPERATIONS ====================

  fun splitClipAtPlayhead() {
    val state = _uiState.value
    val playhead = state.playheadMs
    val targetClip = state.selectedClip?.takeIf { playhead in (it.timelineStartMs + 150L) until (it.timelineEndMs - 150L) }
      ?: state.project.tracks
        .filter { !it.isLocked }
        .flatMap { it.clips }
        .firstOrNull { playhead in (it.timelineStartMs + 150L) until (it.timelineEndMs - 150L) }

    if (targetClip == null) {
      _uiState.update { it.copy(lastActionMessage = "Move playhead inside an unlocked clip to Split") }
      return
    }

    val leftDuration = playhead - targetClip.timelineStartMs
    val rightDuration = targetClip.durationMs - leftDuration

    val leftClip = targetClip.copy(
      durationMs = leftDuration,
      keyframes = targetClip.keyframes.filter { it.offsetMs <= leftDuration },
    )
    val rightClip = targetClip.copy(
      id = UUID.randomUUID().toString(),
      name = "${targetClip.name} (Part 2)",
      timelineStartMs = playhead,
      durationMs = rightDuration,
      sourceInMs = targetClip.sourceInMs + (leftDuration * targetClip.speed).toLong(),
      transition = TransitionConfig(TransitionType.NONE, 500L),
      keyframes = targetClip.keyframes
        .filter { it.offsetMs > leftDuration }
        .map { it.copy(id = UUID.randomUUID().toString(), offsetMs = it.offsetMs - leftDuration) },
    )

    commitProjectMutation("Split '${targetClip.name}' at ${KeyframeColorEngine.formatTimecode(playhead, state.project.config.fps)}") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.isLocked) track
          else track.copy(
            clips = track.clips.flatMap { c ->
              if (c.id == targetClip.id) listOf(leftClip, rightClip) else listOf(c)
            }
          )
        }
      )
    }
    _uiState.update { it.copy(selectedClipId = rightClip.id) }
  }

  fun trimClipStartToPlayhead() {
    val state = _uiState.value
    val clip = state.selectedClip ?: return
    val playhead = state.playheadMs
    if (playhead <= clip.timelineStartMs || playhead >= clip.timelineEndMs - 200L) {
      // Nudge trim in by 250ms if playhead is not inside clip
      adjustSelectedClipTiming(deltaStartMs = 0L, deltaDurationMs = -250L)
      return
    }
    val cutMs = playhead - clip.timelineStartMs
    commitProjectMutation("Trim In '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.isLocked) track
          else track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) {
                c.copy(
                  timelineStartMs = playhead,
                  durationMs = (c.durationMs - cutMs).coerceAtLeast(250L),
                  sourceInMs = c.sourceInMs + cutMs,
                )
              } else c
            }
          )
        }
      )
    }
  }

  fun trimClipEndToPlayhead() {
    val state = _uiState.value
    val clip = state.selectedClip ?: return
    val playhead = state.playheadMs
    if (playhead <= clip.timelineStartMs + 200L || playhead >= clip.timelineEndMs) {
      adjustSelectedClipTiming(deltaStartMs = 0L, deltaDurationMs = 250L)
      return
    }
    val newDuration = (playhead - clip.timelineStartMs).coerceAtLeast(250L)
    commitProjectMutation("Trim Out '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.isLocked) track
          else track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(durationMs = newDuration) else c
            }
          )
        }
      )
    }
  }

  fun adjustSelectedClipTiming(deltaStartMs: Long, deltaDurationMs: Long) {
    val clip = _uiState.value.selectedClip ?: return
    val newStart = (clip.timelineStartMs + deltaStartMs).coerceAtLeast(0L)
    val newDur = (clip.durationMs + deltaDurationMs).coerceIn(300L, 60_000L)
    commitProjectMutation("Adjust Timing '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.isLocked) track
          else track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(timelineStartMs = newStart, durationMs = newDur) else c
            }
          )
        }
      )
    }
  }

  fun duplicateSelectedClip() {
    val clip = _uiState.value.selectedClip ?: return
    val duplicate = clip.copy(
      id = UUID.randomUUID().toString(),
      name = "${clip.name} Copy",
      timelineStartMs = clip.timelineEndMs,
      keyframes = clip.keyframes.map { it.copy(id = UUID.randomUUID().toString()) },
    )
    commitProjectMutation("Duplicate '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.type == clip.trackType && !track.isLocked) {
            track.copy(clips = (track.clips + duplicate).sortedBy { it.timelineStartMs })
          } else track
        }
      )
    }
    _uiState.update { it.copy(selectedClipId = duplicate.id) }
  }

  fun deleteSelectedClip() {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Delete '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.isLocked) track
          else track.copy(clips = track.clips.filterNot { it.id == clip.id })
        }
      )
    }
  }

  fun freezeFrameAtPlayhead() {
    val state = _uiState.value
    val clip = state.selectedClip ?: return
    val freezeClip = clip.copy(
      id = UUID.randomUUID().toString(),
      name = "${clip.name} [Freeze]",
      timelineStartMs = clip.timelineEndMs,
      durationMs = 1_500L,
      isFrozen = true,
      speed = 1.0f,
    )
    commitProjectMutation("Freeze Frame '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          if (track.type == clip.trackType && !track.isLocked) {
            track.copy(clips = (track.clips + freezeClip).sortedBy { it.timelineStartMs })
          } else track
        }
      )
    }
    _uiState.update { it.copy(selectedClipId = freezeClip.id) }
  }

  fun reverseSelectedClip() {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Reverse '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(isReversed = !c.isReversed) else c
            }
          )
        }
      )
    }
  }

  fun extractAudioToTrack() {
    val clip = _uiState.value.selectedClip ?: return
    if (clip.trackType == TrackType.AUDIO) return
    val extractedAudio = TimelineClip(
      id = UUID.randomUUID().toString(),
      materialId = "aud_${clip.materialId}",
      name = "Extracted_${clip.name.substringBeforeLast('.')}.m4a",
      trackType = TrackType.AUDIO,
      timelineStartMs = clip.timelineStartMs,
      durationMs = clip.durationMs,
      sourceInMs = clip.sourceInMs,
      sourceDurationMs = clip.sourceDurationMs,
      sourceUri = clip.sourceUri,
      volume = clip.volume,
    )
    commitProjectMutation("Extract Audio from '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          when (track.type) {
            TrackType.AUDIO -> track.copy(clips = (track.clips + extractedAudio).sortedBy { it.timelineStartMs })
            clip.trackType -> track.copy(
              clips = track.clips.map { c -> if (c.id == clip.id) c.copy(volume = 0f) else c }
            )
            else -> track
          }
        }
      )
    }
  }

  fun addMarkerAtPlayhead(label: String = "") {
    val playhead = _uiState.value.playheadMs
    val idx = _uiState.value.project.markers.size + 1
    val marker = TimelineMarker(
      timeMs = playhead,
      label = label.ifBlank { "Marker #$idx" },
      colorHex = if (idx % 2 == 0) 0xFFFFAB00 else 0xFF00E5FF,
    )
    commitProjectMutation("Add Marker at ${KeyframeColorEngine.formatTimecode(playhead)}") { proj ->
      proj.copy(markers = (proj.markers + marker).sortedBy { it.timeMs })
    }
  }

  fun toggleTrackMute(trackId: String) {
    commitProjectMutation("Toggle Track Mute") { proj ->
      proj.copy(
        tracks = proj.tracks.map { t ->
          if (t.id == trackId) t.copy(isMuted = !t.isMuted) else t
        }
      )
    }
  }

  fun toggleTrackLock(trackId: String) {
    commitProjectMutation("Toggle Track Lock") { proj ->
      proj.copy(
        tracks = proj.tracks.map { t ->
          if (t.id == trackId) t.copy(isLocked = !t.isLocked) else t
        }
      )
    }
  }

  fun toggleTrackHidden(trackId: String) {
    commitProjectMutation("Toggle Track Visibility") { proj ->
      proj.copy(
        tracks = proj.tracks.map { t ->
          if (t.id == trackId) t.copy(isHidden = !t.isHidden) else t
        }
      )
    }
  }

  // ==================== MEDIA & PROCEDURAL ASSET ADDITION ====================

  fun addProceduralStudioClip(theme: ProceduralClipTheme, targetTrackType: TrackType = TrackType.VIDEO) {
    val proj = _uiState.value.project
    val track = proj.tracks.firstOrNull { it.type == targetTrackType }
    val startMs = if (targetTrackType == TrackType.OVERLAY) {
      _uiState.value.playheadMs
    } else {
      track?.clips?.maxOfOrNull { it.timelineEndMs } ?: 0L
    }
    val newClip = TimelineClip(
      name = "${theme.title.replace(' ', '_')}.mp4",
      trackType = targetTrackType,
      timelineStartMs = startMs,
      durationMs = 3_000L,
      proceduralTheme = theme,
      scale = if (targetTrackType == TrackType.OVERLAY) 0.42f else 1.0f,
      positionX = if (targetTrackType == TrackType.OVERLAY) 0.45f else 0.0f,
      positionY = if (targetTrackType == TrackType.OVERLAY) -0.45f else 0.0f,
      transition = TransitionConfig(TransitionType.CROSS_DISSOLVE, 500L),
    )
    commitProjectMutation("Add '${theme.title}' to ${targetTrackType.code}") { p ->
      p.copy(
        tracks = p.tracks.map { t ->
          if (t.type == targetTrackType) t.copy(clips = (t.clips + newClip).sortedBy { it.timelineStartMs })
          else t
        }
      )
    }
    _uiState.update { it.copy(selectedClipId = newClip.id) }
  }

  fun importDeviceMediaUri(context: Context, uri: Uri, targetTrackType: TrackType) {
    viewModelScope.launch {
      val durationMs = RealMp4ExportEngine.inspectMediaUriDurationMs(context, uri).coerceIn(1_000L, 30_000L)
      val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Device_Media.mp4"
      val proj = _uiState.value.project
      val track = proj.tracks.firstOrNull { it.type == targetTrackType }
      val startMs = track?.clips?.maxOfOrNull { it.timelineEndMs } ?: 0L

      val newClip = TimelineClip(
        name = fileName,
        trackType = targetTrackType,
        timelineStartMs = startMs,
        durationMs = durationMs,
        sourceDurationMs = durationMs,
        sourceUri = uri.toString(),
        proceduralTheme = ProceduralClipTheme.CYBERPUNK_GRID,
      )
      commitProjectMutation("Import Device Media '$fileName'") { p ->
        p.copy(
          tracks = p.tracks.map { t ->
            if (t.type == targetTrackType) t.copy(clips = (t.clips + newClip).sortedBy { it.timelineStartMs })
            else t
          }
        )
      }
      _uiState.update { it.copy(selectedClipId = newClip.id) }
    }
  }

  // ==================== INSPECTOR: TRANSFORM, KEYFRAMES, COLOR, AUDIO ====================

  fun updateSelectedClipTransform(
    scale: Float? = null,
    positionX: Float? = null,
    positionY: Float? = null,
    rotationDeg: Float? = null,
    opacity: Float? = null,
    mirrorH: Boolean? = null,
    mirrorV: Boolean? = null,
  ) {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Transform '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) {
                c.copy(
                  scale = scale ?: c.scale,
                  positionX = positionX ?: c.positionX,
                  positionY = positionY ?: c.positionY,
                  rotationDeg = rotationDeg ?: c.rotationDeg,
                  opacity = opacity ?: c.opacity,
                  mirrorHorizontal = mirrorH ?: c.mirrorHorizontal,
                  mirrorVertical = mirrorV ?: c.mirrorVertical,
                )
              } else c
            }
          )
        }
      )
    }
  }

  fun resetSelectedClipTransform() {
    updateSelectedClipTransform(
      scale = 1.0f,
      positionX = 0f,
      positionY = 0f,
      rotationDeg = 0f,
      opacity = 1.0f,
      mirrorH = false,
      mirrorV = false,
    )
  }

  fun addOrUpdateKeyframeAtPlayhead(easing: EasingType = EasingType.EASE_IN_OUT) {
    val state = _uiState.value
    val clip = state.selectedClip ?: return
    val offsetMs = (state.playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
    val currentInterp = KeyframeColorEngine.evaluateTransformAt(clip, state.playheadMs)
    val newKf = ClipKeyframe(
      offsetMs = offsetMs,
      scale = currentInterp.scale,
      positionX = currentInterp.positionX,
      positionY = currentInterp.positionY,
      rotationDeg = currentInterp.rotationDeg,
      opacity = currentInterp.opacity,
      easing = easing,
    )

    commitProjectMutation("Add Keyframe ♦ at +${offsetMs}ms") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) {
                val filtered = c.keyframes.filterNot { kotlin.math.abs(it.offsetMs - offsetMs) < 80L }
                c.copy(keyframes = (filtered + newKf).sortedBy { it.offsetMs })
              } else c
            }
          )
        }
      )
    }
  }

  fun deleteKeyframe(keyframeId: String) {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Remove Keyframe ♦") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(keyframes = c.keyframes.filterNot { it.id == keyframeId })
              else c
            }
          )
        }
      )
    }
  }

  fun updateSelectedClipColorGrading(
    brightness: Float? = null,
    contrast: Float? = null,
    saturation: Float? = null,
    temperature: Float? = null,
    vignette: Float? = null,
    filterPreset: FilterPreset? = null,
  ) {
    val clip = _uiState.value.selectedClip ?: return
    val cg = clip.colorGrading
    val updatedCg = cg.copy(
      brightness = brightness ?: cg.brightness,
      contrast = contrast ?: cg.contrast,
      saturation = saturation ?: cg.saturation,
      temperature = temperature ?: cg.temperature,
      vignette = vignette ?: cg.vignette,
      filterPreset = filterPreset ?: cg.filterPreset,
    )
    commitProjectMutation("Color Grade '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(colorGrading = updatedCg) else c
            }
          )
        }
      )
    }
  }

  fun resetSelectedClipColorGrading() {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Reset Color Grade '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) c.copy(colorGrading = ColorGrading()) else c
            }
          )
        }
      )
    }
  }

  fun addFxTrackLutClip(preset: FilterPreset) {
    val playhead = _uiState.value.playheadMs
    val fxClip = TimelineClip(
      name = "LUT: ${preset.displayName}",
      trackType = TrackType.EFFECT,
      timelineStartMs = playhead,
      durationMs = 3_000L,
      colorGrading = ColorGrading(filterPreset = preset),
    )
    commitProjectMutation("Add FX Track LUT '${preset.displayName}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { t ->
          if (t.type == TrackType.EFFECT) t.copy(clips = (t.clips + fxClip).sortedBy { it.timelineStartMs })
          else t
        }
      )
    }
  }

  fun updateSelectedClipTransition(type: TransitionType, durationMs: Long? = null) {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Set Transition '${type.displayName}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) {
                c.copy(
                  transition = TransitionConfig(
                    type = type,
                    durationMs = durationMs ?: c.transition.durationMs,
                  )
                )
              } else c
            }
          )
        }
      )
    }
  }

  fun updateSelectedClipSpeedAndVolume(
    speed: Float? = null,
    volume: Float? = null,
    fadeInMs: Long? = null,
    fadeOutMs: Long? = null,
  ) {
    val clip = _uiState.value.selectedClip ?: return
    commitProjectMutation("Audio/Speed '${clip.name}'") { proj ->
      proj.copy(
        tracks = proj.tracks.map { track ->
          track.copy(
            clips = track.clips.map { c ->
              if (c.id == clip.id) {
                c.copy(
                  speed = speed ?: c.speed,
                  volume = volume ?: c.volume,
                  fadeInMs = fadeInMs ?: c.fadeInMs,
                  fadeOutMs = fadeOutMs ?: c.fadeOutMs,
                )
              } else c
            }
          )
        }
      )
    }
  }

  // ==================== SUBTITLES & SRT ENGINE ====================

  fun addSubtitleCueAtPlayhead(text: String = "New Subtitle Caption", secondaryText: String = "ترجمة جديدة") {
    val start = _uiState.value.playheadMs
    val end = start + 2_400L
    val defaultStyle = _uiState.value.project.subtitles.firstOrNull()?.style ?: SubtitleStyle()
    val newCue = SubtitleCue(
      startTimeMs = start,
      endTimeMs = end,
      text = text,
      secondaryText = secondaryText,
      style = defaultStyle,
    )
    commitProjectMutation("Add Subtitle Cue") { proj ->
      val updatedSubs = (proj.subtitles + newCue).sortedBy { it.startTimeMs }
      syncSubtitlesToTextTrack(proj, updatedSubs)
    }
    _uiState.update { it.copy(selectedSubtitleId = newCue.id) }
  }

  fun updateSubtitleCue(cueId: String, text: String, secondaryText: String, startMs: Long, endMs: Long) {
    commitProjectMutation("Edit Subtitle Cue") { proj ->
      val updatedSubs = proj.subtitles.map { s ->
        if (s.id == cueId) {
          s.copy(
            text = text,
            secondaryText = secondaryText,
            startTimeMs = startMs.coerceAtLeast(0L),
            endTimeMs = endMs.coerceAtLeast(startMs + 200L),
          )
        } else s
      }.sortedBy { it.startTimeMs }
      syncSubtitlesToTextTrack(proj, updatedSubs)
    }
  }

  fun deleteSubtitleCue(cueId: String) {
    commitProjectMutation("Delete Subtitle Cue") { proj ->
      val updatedSubs = proj.subtitles.filterNot { it.id == cueId }
      syncSubtitlesToTextTrack(proj, updatedSubs)
    }
  }

  fun shiftAllSubtitles(deltaMs: Long) {
    commitProjectMutation("Shift Subtitles ${if (deltaMs >= 0) "+$deltaMs" else "$deltaMs"}ms") { proj ->
      val updatedSubs = proj.subtitles.map { s ->
        val ns = (s.startTimeMs + deltaMs).coerceAtLeast(0L)
        val ne = (s.endTimeMs + deltaMs).coerceAtLeast(ns + 200L)
        s.copy(startTimeMs = ns, endTimeMs = ne)
      }
      syncSubtitlesToTextTrack(proj, updatedSubs)
    }
  }

  fun updateGlobalSubtitleStyle(
    fontSizeSp: Float? = null,
    bgOpacity: Float? = null,
    verticalPosition: Float? = null,
    showBilingual: Boolean? = null,
    animation: SubtitleAnimation? = null,
    textColorHex: Long? = null,
  ) {
    commitProjectMutation("Update Subtitle Style") { proj ->
      proj.copy(
        subtitles = proj.subtitles.map { s ->
          val st = s.style
          s.copy(
            style = st.copy(
              fontSizeSp = fontSizeSp ?: st.fontSizeSp,
              bgOpacity = bgOpacity ?: st.bgOpacity,
              verticalPosition = verticalPosition ?: st.verticalPosition,
              showBilingual = showBilingual ?: st.showBilingual,
              animation = animation ?: st.animation,
              textColorHex = textColorHex ?: st.textColorHex,
            )
          )
        }
      )
    }
  }

  fun autoGenerateSubtitlesFromTimeline() {
    commitProjectMutation("Auto-Generate Timeline Captions") { proj ->
      val defaultStyle = proj.subtitles.firstOrNull()?.style ?: SubtitleStyle()
      val generated = SrtSubtitleEngine.autoGenerateCuesFromTracks(proj.tracks, defaultStyle)
      syncSubtitlesToTextTrack(proj, generated)
    }
  }

  fun importSrtContent(rawSrt: String) {
    val parsed = SrtSubtitleEngine.parseSrtOrVtt(rawSrt)
    if (parsed.isEmpty()) {
      _uiState.update { it.copy(lastActionMessage = "No valid SRT/VTT subtitle cues found") }
      return
    }
    commitProjectMutation("Import ${parsed.size} SRT Subtitles") { proj ->
      syncSubtitlesToTextTrack(proj, parsed)
    }
  }

  private fun syncSubtitlesToTextTrack(proj: ReaCutProject, subs: List<SubtitleCue>): ReaCutProject {
    val textClips = subs.map { cue ->
      TimelineClip(
        id = "tclip_${cue.id}",
        materialId = "mat_${cue.id}",
        name = cue.text.take(22),
        trackType = TrackType.TEXT,
        timelineStartMs = cue.startTimeMs,
        durationMs = cue.durationMs,
        overlayText = cue.text,
      )
    }
    return proj.copy(
      subtitles = subs,
      tracks = proj.tracks.map { t ->
        if (t.type == TrackType.TEXT) t.copy(clips = textClips) else t
      },
    )
  }

  // ==================== PROJECT & CAPCUT DRAFT I/O ====================

  fun updateProjectConfig(
    name: String? = null,
    aspectRatio: AspectRatioPreset? = null,
    resolution: ResolutionPreset? = null,
    fps: Int? = null,
  ) {
    commitProjectMutation("Update Project Settings") { proj ->
      val cfg = proj.config
      proj.copy(
        config = cfg.copy(
          name = name ?: cfg.name,
          aspectRatio = aspectRatio ?: cfg.aspectRatio,
          resolution = resolution ?: cfg.resolution,
          fps = fps ?: cfg.fps,
        )
      )
    }
  }

  fun createNewProject(name: String, aspectRatio: AspectRatioPreset, fps: Int) {
    pause()
    val newProj = ReaCutProject(
      config = ProjectConfig(
        id = UUID.randomUUID().toString(),
        name = name.ifBlank { "Untitled ReaCut Project" },
        aspectRatio = aspectRatio,
        resolution = ResolutionPreset.RES_1080P,
        fps = fps,
      ),
      tracks = listOf(
        TimelineTrack(
          type = TrackType.VIDEO,
          clips = listOf(
            TimelineClip(
              name = "01_Starter_Canvas.mp4",
              trackType = TrackType.VIDEO,
              timelineStartMs = 0L,
              durationMs = 4_000L,
              proceduralTheme = ProceduralClipTheme.CYBERPUNK_GRID,
            )
          ),
        ),
        TimelineTrack(type = TrackType.OVERLAY, clips = emptyList()),
        TimelineTrack(type = TrackType.TEXT, clips = emptyList()),
        TimelineTrack(type = TrackType.EFFECT, clips = emptyList()),
        TimelineTrack(type = TrackType.AUDIO, clips = emptyList()),
      ),
      subtitles = listOf(
        SubtitleCue(
          startTimeMs = 200L,
          endTimeMs = 3_800L,
          text = name.ifBlank { "New ReaCut Studio Sequence" },
        )
      ),
    )
    undoStack.clear()
    redoStack.clear()
    _uiState.update {
      it.copy(
        project = newProj,
        playheadMs = 0L,
        selectedClipId = newProj.tracks.first().clips.firstOrNull()?.id,
        undoCount = 0,
        redoCount = 0,
        showProjectDrawer = false,
        lastActionMessage = "Created Project '${newProj.config.name}'",
      )
    }
    viewModelScope.launch { repository.saveProject(newProj) }
  }

  fun loadSavedProject(projectId: String) {
    pause()
    viewModelScope.launch {
      val loaded = repository.loadProject(projectId) ?: return@launch
      undoStack.clear()
      redoStack.clear()
      _uiState.update {
        it.copy(
          project = loaded,
          playheadMs = 0L,
          selectedClipId = loaded.tracks.flatMap { t -> t.clips }.firstOrNull()?.id,
          selectedSubtitleId = loaded.subtitles.firstOrNull()?.id,
          undoCount = 0,
          redoCount = 0,
          showProjectDrawer = false,
          lastActionMessage = "Loaded '${loaded.config.name}'",
        )
      }
    }
  }

  fun deleteSavedProject(projectId: String) {
    viewModelScope.launch {
      repository.deleteProject(projectId)
    }
  }

  fun importCapCutDraftJson(rawJson: String) {
    pause()
    val imported = CapCutDraftEngine.importCapCutDraftJson(rawJson)
    commitProjectMutation("Import CapCut draft_content.json") {
      imported
    }
    _uiState.update {
      it.copy(
        playheadMs = 0L,
        selectedClipId = imported.tracks.flatMap { t -> t.clips }.firstOrNull()?.id,
        selectedSubtitleId = imported.subtitles.firstOrNull()?.id,
      )
    }
  }

  // ==================== HARDWARE MP4 VIDEO EXPORT ====================

  fun startHardwareMp4Export(
    context: Context,
    resolution: ResolutionPreset,
    fps: Int,
    burnInSubtitles: Boolean,
  ) {
    pause()
    exportJob?.cancel()
    val projectToExport = _uiState.value.project
    exportJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          exportStatus = ExportStatus.Encoding(0.01f, 0, 100, "Initializing Android MediaCodec H.264 Encoder..."),
        )
      }
      val result = RealMp4ExportEngine.exportProjectToMp4(
        context = context,
        project = projectToExport,
        outputResolution = resolution,
        outputFps = fps,
        burnInSubtitles = burnInSubtitles,
        onProgress = { progressState ->
          _uiState.update { it.copy(exportStatus = progressState) }
        },
      )
      _uiState.update {
        it.copy(
          exportStatus = result,
          lastActionMessage = when (result) {
            is ExportStatus.Completed -> "Exported MP4 (${result.fileSizeBytes / 1024} KB)"
            is ExportStatus.Failed -> "Export Error: ${result.errorMessage}"
            else -> it.lastActionMessage
          },
        )
      }
    }
  }

  fun resetExportStatus() {
    exportJob?.cancel()
    _uiState.update { it.copy(exportStatus = ExportStatus.Idle) }
  }

  // ==================== MODAL VISIBILITY ====================

  fun setShowProjectDrawer(show: Boolean) {
    _uiState.update { it.copy(showProjectDrawer = show) }
  }

  fun setShowExportModal(show: Boolean) {
    if (!show) resetExportStatus()
    _uiState.update { it.copy(showExportModal = show) }
  }

  fun setShowCapCutDraftModal(show: Boolean) {
    _uiState.update { it.copy(showCapCutDraftModal = show) }
  }

  fun setShowLicenseArchitectureModal(show: Boolean) {
    _uiState.update { it.copy(showLicenseArchitectureModal = show) }
  }

  class Factory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EditorViewModel(repository) as T
    }
  }
}
