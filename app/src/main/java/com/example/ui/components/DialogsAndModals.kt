package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.core.AspectRatioPreset
import com.example.core.CapCutDraftEngine
import com.example.core.ExportStatus
import com.example.core.ResolutionPreset
import com.example.core.SrtSubtitleEngine
import com.example.data.ProjectEntity
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
import com.example.ui.theme.ReaCutTextPrimary
import com.example.ui.theme.ReaCutTextSecondary
import java.io.File

@Composable
fun ExportStudioModal(
  state: EditorUiState,
  onStartMp4Export: (ResolutionPreset, Int, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var exportTab by remember { mutableIntStateOf(0) } // 0=MP4, 1=CapCut JSON, 2=SRT/VTT, 3=ReaCut JSON
  var selectedRes by remember { mutableStateOf(state.project.config.resolution) }
  var selectedFps by remember { mutableIntStateOf(state.project.config.fps) }
  var burnInSubs by remember { mutableStateOf(true) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = ReaCutSurface,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(12.dp))
        .testTag("export_modal_dialog"),
    ) {
      Column(
        modifier = Modifier
          .padding(14.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "ReaCut Studio Export Engine",
            style = MaterialTheme.typography.titleMedium,
            color = ReaCutTextPrimary,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaCutTextSecondary)
          }
        }

        // Export Format Selector
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ChipOption("MP4 Video (H.264)", exportTab == 0, "export_tab_mp4") { exportTab = 0 }
          ChipOption("CapCut draft_content.json", exportTab == 1, "export_tab_capcut") { exportTab = 1 }
          ChipOption("SubRip .SRT / .VTT", exportTab == 2, "export_tab_srt") { exportTab = 2 }
          ChipOption("ReaCut .reacut.json", exportTab == 3, "export_tab_reacut") { exportTab = 3 }
        }

        when (exportTab) {
          0 -> {
            Text(
              text = "Hardware MediaCodec H.264/AVC + MediaMuxer Renderer",
              style = MaterialTheme.typography.labelSmall,
              color = ReaCutTeal,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(ResolutionPreset.RES_720P, ResolutionPreset.RES_1080P).forEach { res ->
                ChipOption(res.label, selectedRes == res, "export_res_${res.name}") { selectedRes = res }
              }
              listOf(24, 30, 60).forEach { fps ->
                ChipOption("${fps}fps", selectedFps == fps, "export_fps_$fps") { selectedFps = fps }
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { burnInSubs = !burnInSubs },
            ) {
              Checkbox(checked = burnInSubs, onCheckedChange = { burnInSubs = it })
              Text(
                text = "Burn-in styled Subtitles & Text Track into MP4 frames",
                style = MaterialTheme.typography.bodySmall,
                color = ReaCutTextPrimary,
              )
            }

            when (val status = state.exportStatus) {
              is ExportStatus.Idle -> {
                Button(
                  onClick = { onStartMp4Export(selectedRes, selectedFps, burnInSubs) },
                  colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_mp4_export_button"),
                ) {
                  Text(
                    text = "Render Playable MP4 Video Now",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
              is ExportStatus.Encoding -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  LinearProgressIndicator(
                    progress = { status.progress },
                    color = ReaCutCyan,
                    trackColor = ReaCutBorder,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Text(
                    text = status.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = ReaCutCyan,
                  )
                }
              }
              is ExportStatus.Completed -> {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ReaCutBgPrimary)
                    .border(1.dp, ReaCutTeal, RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .testTag("export_completed_card"),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ReaCutTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                      text = "MP4 Render Complete (${status.fileSizeBytes / 1024} KB • ${status.totalFrames} frames)",
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = ReaCutTeal,
                    )
                  }
                  Text(
                    text = "Resolution: ${status.width}x${status.height} @ ${status.fps}fps\nPath: ${status.outputFile.absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ReaCutTextSecondary,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = { shareExportedFile(context, status.outputFile, "video/mp4") },
                      colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Share / Open MP4")
                    }
                    Button(
                      onClick = { onStartMp4Export(selectedRes, selectedFps, burnInSubs) },
                      colors = ButtonDefaults.buttonColors(containerColor = ReaCutSurfaceElevated, contentColor = ReaCutTextPrimary),
                    ) {
                      Text("Re-render")
                    }
                  }
                }
              }
              is ExportStatus.Failed -> {
                Text(
                  text = "Export Error: ${status.errorMessage}",
                  style = MaterialTheme.typography.bodySmall,
                  color = ReaCutPlayheadCoral,
                )
                Button(
                  onClick = { onStartMp4Export(selectedRes, selectedFps, burnInSubs) },
                  colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
                ) {
                  Text("Retry MP4 Render")
                }
              }
            }
          }
          1 -> {
            val draftJson = remember(state.project) { CapCutDraftEngine.exportToCapCutDraftJson(state.project) }
            CodeExportPreviewBox(
              title = "Generated CapCut / JianYing draft_content.json (µs timestamps)",
              code = draftJson,
              fileName = "draft_content.json",
              mimeType = "application/json",
            )
          }
          2 -> {
            val srtContent = remember(state.project.subtitles) { SrtSubtitleEngine.exportToSrt(state.project.subtitles) }
            CodeExportPreviewBox(
              title = "Generated SubRip (.srt) Subtitles (${state.project.subtitles.size} cues)",
              code = srtContent,
              fileName = "${state.project.config.name.replace(' ', '_')}.srt",
              mimeType = "text/plain",
            )
          }
          3 -> {
            val reacutJson = remember(state.project) { CapCutDraftEngine.serializeReaCutProject(state.project) }
            CodeExportPreviewBox(
              title = "Lossless ReaCut Project (.reacut.json)",
              code = reacutJson,
              fileName = "${state.project.config.name.replace(' ', '_')}.reacut.json",
              mimeType = "application/json",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CodeExportPreviewBox(
  title: String,
  code: String,
  fileName: String,
  mimeType: String,
) {
  val context = LocalContext.current
  var copied by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = title, style = MaterialTheme.typography.labelSmall, color = ReaCutTeal)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(ReaCutBgDarkest)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(8.dp))
        .padding(8.dp)
        .verticalScroll(rememberScrollState()),
    ) {
      Text(
        text = code,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = ReaCutTextPrimary,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText(fileName, code))
          copied = true
        },
        colors = ButtonDefaults.buttonColors(containerColor = ReaCutSurfaceElevated, contentColor = ReaCutCyan),
        modifier = Modifier.weight(1f),
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (copied) "Copied ✓" else "Copy Text")
      }
      Button(
        onClick = {
          val outDir = File(context.filesDir, "exports").apply { mkdirs() }
          val f = File(outDir, fileName)
          f.writeText(code)
          shareExportedFile(context, f, mimeType)
        },
        colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
        modifier = Modifier.weight(1f),
      ) {
        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("Save / Share $fileName")
      }
    }
  }
}

@Composable
fun CapCutDraftImporterModal(
  state: EditorUiState,
  onPickDraftFileFromDevice: () -> Unit,
  onImportDraftJson: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var jsonInput by remember {
    mutableStateOf(CapCutDraftEngine.generateSampleCapCutDesktopDraftJson())
  }
  val report = state.project.lastImportReport

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = ReaCutSurface,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(12.dp))
        .testTag("capcut_draft_modal"),
    ) {
      Column(
        modifier = Modifier
          .padding(14.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "CapCut / JianYing Draft Importer",
            style = MaterialTheme.typography.titleMedium,
            color = ReaCutTextPrimary,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaCutTextSecondary)
          }
        }

        Text(
          text = "Imports CapCut Desktop / JianYing `draft_content.json` microsecond timelines into ReaCut tracks.",
          style = MaterialTheme.typography.bodySmall,
          color = ReaCutTextSecondary,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ChipOption(
            label = "Browse File (.json)",
            selected = false,
            tag = "pick_draft_file_button",
            onClick = onPickDraftFileFromDevice,
          )
          ChipOption(
            label = "Load Sample CapCut Draft",
            selected = true,
            tag = "load_sample_capcut_draft_button",
            onClick = {
              val sample = CapCutDraftEngine.generateSampleCapCutDesktopDraftJson()
              jsonInput = sample
              onImportDraftJson(sample)
            },
          )
          ChipOption(
            label = "Test Encrypted 2024+ Draft",
            selected = false,
            tag = "test_encrypted_draft_button",
            onClick = {
              val encryptedPayload = "JY_ENC_V2_9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0"
              jsonInput = encryptedPayload
              onImportDraftJson(encryptedPayload)
            },
          )
        }

        // Diagnostic Report Card (if an import was performed)
        if (report != null) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(ReaCutBgDarkest)
              .border(
                width = 1.dp,
                color = if (report.isEncryptedError) ReaCutPlayheadCoral else ReaCutTeal,
                shape = RoundedCornerShape(8.dp),
              )
              .padding(10.dp)
              .testTag("draft_import_report_card"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (report.isEncryptedError) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (report.isEncryptedError) ReaCutPlayheadCoral else ReaCutTeal,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (report.isEncryptedError) {
                  "Encrypted CapCut Draft Detected"
                } else {
                  "Imported: ${report.importedVideoCount} Videos • ${report.importedAudioCount} Audios • ${report.importedSubtitleCount} Texts • ${report.importedEffectCount} FX"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (report.isEncryptedError) ReaCutPlayheadCoral else ReaCutTeal,
              )
            }
            report.warnings.forEach { warn ->
              Text(
                text = "• $warn",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ReaCutTextSecondary,
              )
            }
          }
        }

        OutlinedTextField(
          value = jsonInput,
          onValueChange = { jsonInput = it },
          label = { Text("draft_content.json payload") },
          maxLines = 7,
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .testTag("draft_json_input"),
        )

        Button(
          onClick = { onImportDraftJson(jsonInput) },
          colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("confirm_import_draft_button"),
        ) {
          Text(
            text = "Parse & Import draft_content.json into Timeline",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}

@Composable
fun ProjectManagerModal(
  state: EditorUiState,
  savedProjects: List<ProjectEntity>,
  onCreateProject: (String, AspectRatioPreset, Int) -> Unit,
  onLoadProject: (String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var newName by remember { mutableStateOf("ReaCut Sequence #${savedProjects.size + 1}") }
  var selectedAspect by remember { mutableStateOf(AspectRatioPreset.RATIO_16_9) }
  var selectedFps by remember { mutableIntStateOf(30) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = ReaCutSurface,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(12.dp))
        .testTag("project_manager_modal"),
    ) {
      Column(
        modifier = Modifier
          .padding(14.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "ReaCut Local Projects (Room DB)",
            style = MaterialTheme.typography.titleMedium,
            color = ReaCutTextPrimary,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaCutTextSecondary)
          }
        }

        // Create New Project Section
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ReaCutBgPrimary)
            .border(1.dp, ReaCutBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("Create New Project", style = MaterialTheme.typography.labelLarge, color = ReaCutCyan)
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Project Name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_project_name_input"),
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AspectRatioPreset.entries.forEach { ratio ->
              ChipOption(ratio.label, selectedAspect == ratio, "new_proj_ratio_${ratio.name}") {
                selectedAspect = ratio
              }
            }
          }
          Button(
            onClick = { onCreateProject(newName, selectedAspect, selectedFps) },
            colors = ButtonDefaults.buttonColors(containerColor = ReaCutCyan, contentColor = ReaCutBgDarkest),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_new_project_button"),
          ) {
            Text("Create & Open Project", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
          }
        }

        Text("Saved Projects (${savedProjects.size})", style = MaterialTheme.typography.labelSmall, color = ReaCutTextSecondary)

        savedProjects.forEach { entity ->
          val isCurrent = entity.id == state.project.config.id
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (isCurrent) ReaCutSurfaceElevated else ReaCutBgDarkest)
              .border(1.dp, if (isCurrent) ReaCutCyan else ReaCutBorder, RoundedCornerShape(8.dp))
              .clickable { onLoadProject(entity.id) }
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = entity.name + if (isCurrent) " (Active)" else "",
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) ReaCutCyan else ReaCutTextPrimary,
              )
              Text(
                text = "${entity.aspectRatioLabel} • ${entity.fps}fps • ${entity.clipCount} clips • ${entity.subtitleCount} subs",
                style = MaterialTheme.typography.labelSmall,
                color = ReaCutTextSecondary,
              )
            }
            if (!isCurrent && savedProjects.size > 1) {
              IconButton(onClick = { onDeleteProject(entity.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ReaCutPlayheadCoral)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun LicenseAndArchitectureModal(
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = ReaCutSurface,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .border(1.dp, ReaCutBorder, RoundedCornerShape(12.dp))
        .testTag("license_architecture_modal"),
    ) {
      Column(
        modifier = Modifier
          .padding(14.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "ReaCut License & Android Architecture",
            style = MaterialTheme.typography.titleMedium,
            color = ReaCutTextPrimary,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaCutTextSecondary)
          }
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ReaCutBgDarkest)
            .border(1.dp, ReaCutBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Upstream Project: Xiangyu-CAS/rea-capcut (ReaCut)",
            style = MaterialTheme.typography.labelLarge,
            color = ReaCutCyan,
          )
          Text(
            text = "MIT License • Copyright (c) Xiangyu-CAS\n" +
              "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.",
            style = MaterialTheme.typography.labelSmall,
            color = ReaCutTextSecondary,
          )
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ReaCutBgDarkest)
            .border(1.dp, ReaCutBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Native Android Conversion & Technical Mapping",
            style = MaterialTheme.typography.labelLarge,
            color = ReaCutTeal,
          )
          Text(
            text = "• src/core/ (Project, Timeline, Drafts, Subtitles) → com.example.core (ReaCutModels, CapCutDraftEngine, SrtSubtitleEngine, KeyframeColorEngine)\n" +
              "• Desktop FFmpeg CLI Export → RealMp4ExportEngine using Android Hardware MediaCodec (H.264/AVC) + MediaMuxer + Canvas Compositor\n" +
              "• src/editor/ (Undo/Redo, Preview State) → com.example.editor.EditorViewModel + Jetpack Media3 ExoPlayer\n" +
              "• src/components/ (Desktop NLE UI) → Jetpack Compose Touch-Optimized Multi-Track NLE UI\n" +
              "• Encrypted CapCut Drafts (Post-2024.04): Detected automatically with diagnostic warning per upstream documentation.",
            style = MaterialTheme.typography.labelSmall,
            color = ReaCutTextPrimary,
          )
        }
      }
    }
  }
}

private fun shareExportedFile(context: Context, file: File, mimeType: String) {
  runCatching {
    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
  }
}
