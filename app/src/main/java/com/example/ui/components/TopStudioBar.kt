package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun TopStudioBar(
  state: EditorUiState,
  onOpenProjects: () -> Unit,
  onOpenCapCutDraftModal: () -> Unit,
  onOpenLicenseModal: () -> Unit,
  onOpenExportModal: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = ReaCutBgDarkest,
    modifier = modifier
      .fillMaxWidth()
      .border(width = 1.dp, color = ReaCutBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // Left: Brand + Project Switcher
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f, fill = false),
      ) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(ReaCutCyan, ReaCutTeal),
              )
            )
            .clickable { onOpenProjects() }
            .testTag("projects_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.MovieFilter,
            contentDescription = "ReaCut Projects",
            tint = ReaCutBgDarkest,
            modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenProjects() }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = state.project.config.name,
              style = MaterialTheme.typography.titleSmall,
              color = ReaCutTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(ReaCutSurfaceElevated)
                .border(1.dp, ReaCutBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
                .testTag("project_ratio_badge"),
            ) {
              Text(
                text = "${state.project.config.aspectRatio.ratioWidth}:${state.project.config.aspectRatio.ratioHeight} • ${state.project.config.fps}fps",
                style = MaterialTheme.typography.labelSmall,
                color = ReaCutCyan,
              )
            }
          }
          Text(
            text = state.lastActionMessage,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = ReaCutTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Right: Undo, Redo, Export
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onUndo,
          enabled = state.undoCount > 0,
          modifier = Modifier
            .size(34.dp)
            .testTag("undo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            tint = if (state.undoCount > 0) ReaCutTextPrimary else ReaCutTextMuted,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onRedo,
          enabled = state.redoCount > 0,
          modifier = Modifier
            .size(34.dp)
            .testTag("redo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            tint = if (state.redoCount > 0) ReaCutTextPrimary else ReaCutTextMuted,
            modifier = Modifier.size(18.dp),
          )
        }

        Button(
          onClick = onOpenExportModal,
          colors = ButtonDefaults.buttonColors(
            containerColor = ReaCutCyan,
            contentColor = ReaCutBgDarkest,
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .height(32.dp)
            .testTag("export_button"),
        ) {
          Icon(
            imageVector = Icons.Default.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Export",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}

/**
 * Floating Collapsible Vertical Side Shortcut Rail for Mobile Phone Ergonomics.
 * Replaces bulky permanently stacked panels with clean side shortcuts that open slide-over drawers on demand.
 */
@Composable
fun MobileSideShortcutRail(
  state: EditorUiState,
  onToggleSideRail: () -> Unit,
  onSelectDockTab: (StudioDockTab) -> Unit,
  onToggleQuickToolsSheet: () -> Unit,
  onOpenCapCutDraftModal: () -> Unit,
  onOpenProjects: () -> Unit,
  onOpenLicenseModal: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Collapse / Expand Handle Pill
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        .background(ReaCutSurfaceElevated.copy(alpha = 0.94f))
        .border(
          width = 1.dp,
          color = ReaCutCyan.copy(alpha = 0.6f),
          shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
        )
        .clickable { onToggleSideRail() }
        .padding(horizontal = 4.dp, vertical = 12.dp)
        .testTag("side_rail_toggle"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (state.isSideRailExpanded) {
          Icons.AutoMirrored.Filled.KeyboardArrowRight
        } else {
          Icons.AutoMirrored.Filled.KeyboardArrowLeft
        },
        contentDescription = if (state.isSideRailExpanded) "Collapse Side Shortcuts" else "Expand Side Shortcuts",
        tint = ReaCutCyan,
        modifier = Modifier.size(18.dp),
      )
    }

    AnimatedVisibility(
      visible = state.isSideRailExpanded,
      enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
      exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
          .background(ReaCutBgDarkest.copy(alpha = 0.92f))
          .border(
            width = 1.dp,
            color = ReaCutBorder,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
          )
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 6.dp, vertical = 8.dp)
          .testTag("mobile_side_shortcut_rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SideRailShortcutButton(
          icon = Icons.Default.VideoLibrary,
          label = "Media",
          tag = "dock_tab_media",
          isActive = state.isInspectorDrawerOpen && state.activeDockTab == StudioDockTab.MEDIA,
          accentColor = ReaCutCyan,
          onClick = { onSelectDockTab(StudioDockTab.MEDIA) },
        )
        SideRailShortcutButton(
          icon = Icons.Default.ClosedCaption,
          label = "Subs/SRT",
          tag = "dock_tab_subtitles",
          isActive = state.isInspectorDrawerOpen && state.activeDockTab == StudioDockTab.SUBTITLES,
          accentColor = ReaCutAmber,
          onClick = { onSelectDockTab(StudioDockTab.SUBTITLES) },
        )
        SideRailShortcutButton(
          icon = Icons.Default.ColorLens,
          label = "LUT & FX",
          tag = "dock_tab_color_fx",
          isActive = state.isInspectorDrawerOpen && state.activeDockTab == StudioDockTab.COLOR_FX,
          accentColor = Color(0xFFFF4081),
          onClick = { onSelectDockTab(StudioDockTab.COLOR_FX) },
        )
        SideRailShortcutButton(
          icon = Icons.Default.Transform,
          label = "Keyframe",
          tag = "dock_tab_transform",
          isActive = state.isInspectorDrawerOpen && state.activeDockTab == StudioDockTab.TRANSFORM,
          accentColor = ReaCutTeal,
          onClick = { onSelectDockTab(StudioDockTab.TRANSFORM) },
        )
        SideRailShortcutButton(
          icon = Icons.Default.Speed,
          label = "Audio/Spd",
          tag = "dock_tab_speed_audio",
          isActive = state.isInspectorDrawerOpen && state.activeDockTab == StudioDockTab.SPEED_AUDIO,
          accentColor = Color(0xFF00E676),
          onClick = { onSelectDockTab(StudioDockTab.SPEED_AUDIO) },
        )
        SideRailShortcutButton(
          icon = Icons.Default.Build,
          label = "Cut Tools",
          tag = "side_shortcut_edit_tools",
          isActive = state.showQuickToolsSheet,
          accentColor = ReaCutCyan,
          onClick = onToggleQuickToolsSheet,
        )
        SideRailShortcutButton(
          icon = Icons.Default.FileUpload,
          label = "CapCut",
          tag = "capcut_draft_button",
          isActive = state.showCapCutDraftModal,
          accentColor = ReaCutTeal,
          onClick = onOpenCapCutDraftModal,
        )
        SideRailShortcutButton(
          icon = Icons.Default.FolderOpen,
          label = "Projects",
          tag = "side_shortcut_projects",
          isActive = state.showProjectDrawer,
          accentColor = ReaCutTextSecondary,
          onClick = onOpenProjects,
        )
        SideRailShortcutButton(
          icon = Icons.Default.Info,
          label = "About",
          tag = "license_info_button",
          isActive = state.showLicenseArchitectureModal,
          accentColor = ReaCutTextSecondary,
          onClick = onOpenLicenseModal,
        )
      }
    }
  }
}

@Composable
private fun SideRailShortcutButton(
  icon: ImageVector,
  label: String,
  tag: String,
  isActive: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .width(52.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isActive) accentColor.copy(alpha = 0.22f) else ReaCutSurfaceElevated)
      .border(
        width = 1.dp,
        color = if (isActive) accentColor else ReaCutBorder,
        shape = RoundedCornerShape(8.dp),
      )
      .clickable(onClick = onClick)
      .padding(vertical = 5.dp, horizontal = 2.dp)
      .testTag(tag),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (isActive) accentColor else ReaCutTextPrimary,
      modifier = Modifier.size(17.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 8.5.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
      ),
      color = if (isActive) accentColor else ReaCutTextSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Ultra-slim essential bar between Preview Monitor and Timeline on mobile.
 * Keeps only essential instant actions (Split, Keyframe, Delete, Zoom) and opens full Cut Tools via side shortcut.
 */
@Composable
fun CompactMobileActionStrip(
  state: EditorUiState,
  onSplit: () -> Unit,
  onAddKeyframe: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onOpenAllCutTools: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasSelectedClip = state.selectedClip != null

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(ReaCutSurface)
      .border(width = 0.5.dp, color = ReaCutBorder)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      QuickToolButton(
        icon = Icons.Default.ContentCut,
        label = "Split",
        tag = "action_split",
        accentColor = ReaCutCyan,
        enabled = true,
        onClick = onSplit,
      )
      QuickToolButton(
        icon = Icons.Default.Key,
        label = "+♦ KF",
        tag = "action_add_keyframe",
        accentColor = ReaCutAmber,
        enabled = hasSelectedClip,
        onClick = onAddKeyframe,
      )
      QuickToolButton(
        icon = Icons.Default.ContentCopy,
        label = "Copy",
        tag = "action_duplicate",
        enabled = hasSelectedClip,
        onClick = onDuplicate,
      )
      QuickToolButton(
        icon = Icons.Default.DeleteOutline,
        label = "Del",
        tag = "action_delete",
        accentColor = ReaCutPlayheadCoral,
        enabled = hasSelectedClip,
        onClick = onDelete,
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      QuickToolButton(
        icon = Icons.Default.ZoomOut,
        label = "-",
        tag = "action_zoom_out",
        onClick = onZoomOut,
      )
      QuickToolButton(
        icon = Icons.Default.ZoomIn,
        label = "+",
        tag = "action_zoom_in",
        onClick = onZoomIn,
      )
      QuickToolButton(
        icon = Icons.Default.Build,
        label = "More",
        tag = "open_more_cut_tools_button",
        accentColor = if (state.showQuickToolsSheet) ReaCutCyan else ReaCutTeal,
        onClick = onOpenAllCutTools,
      )
    }
  }
}

/**
 * Slide-over sheet displaying all secondary clip editing tools (Trim In/Out, Freeze, Reverse, Extract Audio, Marker, Snap, Zoom)
 * when opened from the Side Shortcut Rail or "More" button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlideOutQuickToolsSheet(
  state: EditorUiState,
  onSplit: () -> Unit,
  onTrimStart: () -> Unit,
  onTrimEnd: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onAddKeyframe: () -> Unit,
  onFreeze: () -> Unit,
  onReverse: () -> Unit,
  onExtractAudio: () -> Unit,
  onAddMarker: () -> Unit,
  onToggleSnap: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasSelectedClip = state.selectedClip != null

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
      .background(ReaCutSurface.copy(alpha = 0.97f))
      .border(1.dp, ReaCutCyan.copy(alpha = 0.5f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
      .padding(12.dp)
      .testTag("quick_tools_sheet"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Build, contentDescription = null, tint = ReaCutCyan, modifier = Modifier.size(16.dp))
        Text(
          text = "Clip & Timeline Editing Tools",
          style = MaterialTheme.typography.titleSmall,
          color = ReaCutTextPrimary,
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(28.dp)
          .testTag("close_quick_tools_button"),
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close Cut Tools", tint = ReaCutTextSecondary)
      }
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      QuickToolButton(
        icon = Icons.Default.ContentCut,
        label = "Split at Playhead",
        tag = "sheet_action_split",
        accentColor = ReaCutCyan,
        enabled = true,
        onClick = onSplit,
      )
      QuickToolButton(
        icon = Icons.Default.VerticalAlignTop,
        label = "Trim In",
        tag = "action_trim_in",
        enabled = hasSelectedClip,
        onClick = onTrimStart,
      )
      QuickToolButton(
        icon = Icons.Default.VerticalAlignBottom,
        label = "Trim Out",
        tag = "action_trim_out",
        enabled = hasSelectedClip,
        onClick = onTrimEnd,
      )
      QuickToolButton(
        icon = Icons.Default.Key,
        label = "Add Keyframe ♦",
        tag = "sheet_action_add_keyframe",
        accentColor = ReaCutAmber,
        enabled = hasSelectedClip,
        onClick = onAddKeyframe,
      )
      QuickToolButton(
        icon = Icons.Default.ContentCopy,
        label = "Duplicate Clip",
        tag = "sheet_action_duplicate",
        enabled = hasSelectedClip,
        onClick = onDuplicate,
      )
      QuickToolButton(
        icon = Icons.Default.DeleteOutline,
        label = "Delete Clip",
        tag = "sheet_action_delete",
        accentColor = ReaCutPlayheadCoral,
        enabled = hasSelectedClip,
        onClick = onDelete,
      )
      QuickToolButton(
        icon = Icons.Default.AcUnit,
        label = "Freeze Frame (1.5s)",
        tag = "action_freeze",
        enabled = hasSelectedClip,
        onClick = onFreeze,
      )
      QuickToolButton(
        icon = Icons.Default.SwapHoriz,
        label = if (state.selectedClip?.isReversed == true) "Reversed ✓" else "Reverse Clip",
        tag = "action_reverse",
        accentColor = if (state.selectedClip?.isReversed == true) ReaCutTeal else null,
        enabled = hasSelectedClip,
        onClick = onReverse,
      )
      QuickToolButton(
        icon = Icons.Default.GraphicEq,
        label = "Extract Audio to A1",
        tag = "action_extract_audio",
        enabled = hasSelectedClip,
        onClick = onExtractAudio,
      )
      QuickToolButton(
        icon = Icons.Default.BookmarkAdd,
        label = "Add Marker",
        tag = "action_add_marker",
        onClick = onAddMarker,
      )
      QuickToolButton(
        icon = Icons.Default.GridOn,
        label = if (state.snapEnabled) "Snap: ON" else "Snap: OFF",
        tag = "action_toggle_snap",
        accentColor = if (state.snapEnabled) ReaCutTeal else null,
        onClick = onToggleSnap,
      )
      QuickToolButton(
        icon = Icons.Default.ZoomOut,
        label = "Zoom Out Timeline",
        tag = "sheet_action_zoom_out",
        onClick = onZoomOut,
      )
      QuickToolButton(
        icon = Icons.Default.ZoomIn,
        label = "Zoom In Timeline",
        tag = "sheet_action_zoom_in",
        onClick = onZoomIn,
      )
    }
  }
}

@Composable
fun QuickActionToolbar(
  state: EditorUiState,
  onSplit: () -> Unit,
  onTrimStart: () -> Unit,
  onTrimEnd: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onAddKeyframe: () -> Unit,
  onFreeze: () -> Unit,
  onReverse: () -> Unit,
  onExtractAudio: () -> Unit,
  onAddMarker: () -> Unit,
  onToggleSnap: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val hasSelectedClip = state.selectedClip != null

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(ReaCutSurface)
      .border(width = 0.5.dp, color = ReaCutBorder)
      .horizontalScroll(scrollState)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    QuickToolButton(
      icon = Icons.Default.ContentCut,
      label = "Split",
      tag = "action_split",
      accentColor = ReaCutCyan,
      enabled = true,
      onClick = onSplit,
    )
    QuickToolButton(
      icon = Icons.Default.VerticalAlignTop,
      label = "Trim In",
      tag = "action_trim_in",
      enabled = hasSelectedClip,
      onClick = onTrimStart,
    )
    QuickToolButton(
      icon = Icons.Default.VerticalAlignBottom,
      label = "Trim Out",
      tag = "action_trim_out",
      enabled = hasSelectedClip,
      onClick = onTrimEnd,
    )
    QuickToolButton(
      icon = Icons.Default.Key,
      label = "Keyframe ♦",
      tag = "action_add_keyframe",
      accentColor = ReaCutAmber,
      enabled = hasSelectedClip,
      onClick = onAddKeyframe,
    )
    QuickToolButton(
      icon = Icons.Default.ContentCopy,
      label = "Duplicate",
      tag = "action_duplicate",
      enabled = hasSelectedClip,
      onClick = onDuplicate,
    )
    QuickToolButton(
      icon = Icons.Default.DeleteOutline,
      label = "Delete",
      tag = "action_delete",
      accentColor = ReaCutPlayheadCoral,
      enabled = hasSelectedClip,
      onClick = onDelete,
    )
    QuickToolButton(
      icon = Icons.Default.AcUnit,
      label = "Freeze",
      tag = "action_freeze",
      enabled = hasSelectedClip,
      onClick = onFreeze,
    )
    QuickToolButton(
      icon = Icons.Default.SwapHoriz,
      label = if (state.selectedClip?.isReversed == true) "Reversed ✓" else "Reverse",
      tag = "action_reverse",
      accentColor = if (state.selectedClip?.isReversed == true) ReaCutTeal else null,
      enabled = hasSelectedClip,
      onClick = onReverse,
    )
    QuickToolButton(
      icon = Icons.Default.GraphicEq,
      label = "Extract A1",
      tag = "action_extract_audio",
      enabled = hasSelectedClip,
      onClick = onExtractAudio,
    )
    QuickToolButton(
      icon = Icons.Default.BookmarkAdd,
      label = "+Marker",
      tag = "action_add_marker",
      onClick = onAddMarker,
    )
    QuickToolButton(
      icon = Icons.Default.GridOn,
      label = if (state.snapEnabled) "Snap ON" else "Snap OFF",
      tag = "action_toggle_snap",
      accentColor = if (state.snapEnabled) ReaCutTeal else null,
      onClick = onToggleSnap,
    )
    QuickToolButton(
      icon = Icons.Default.ZoomOut,
      label = "Zoom -",
      tag = "action_zoom_out",
      onClick = onZoomOut,
    )
    QuickToolButton(
      icon = Icons.Default.ZoomIn,
      label = "Zoom +",
      tag = "action_zoom_in",
      onClick = onZoomIn,
    )
  }
}

@Composable
private fun QuickToolButton(
  icon: ImageVector,
  label: String,
  tag: String,
  accentColor: Color? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val tint = when {
    !enabled -> ReaCutTextMuted
    accentColor != null -> accentColor
    else -> ReaCutTextPrimary
  }
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(ReaCutSurfaceElevated)
      .border(
        width = 1.dp,
        color = if (accentColor != null && enabled) accentColor.copy(alpha = 0.45f) else ReaCutBorder,
        shape = RoundedCornerShape(6.dp),
      )
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 5.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
      color = tint,
    )
  }
}
