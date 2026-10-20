package com.example.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Port of ReaCut's CapCut / JianYing `draft_content.json` lossy importer/exporter
 * and native `.reacut.json` project serializer.
 */
object CapCutDraftEngine {

  /**
   * Detects if a file content is an encrypted CapCut/JianYing Pro draft (introduced after April 2024).
   */
  fun isEncryptedCapCutDraft(rawContent: String): Boolean {
    val trimmed = rawContent.trim()
    if (trimmed.isEmpty()) return false
    // Unencrypted draft_content.json always starts with '{'
    if (!trimmed.startsWith("{")) {
      return true
    }
    return false
  }

  /**
   * Imports a CapCut / JianYing `draft_content.json` string into a [ReaCutProject],
   * returning both the reconstructed project and a detailed [DraftImportReport].
   */
  fun importCapCutDraftJson(rawJson: String, projectNameOverride: String? = null): ReaCutProject {
    if (isEncryptedCapCutDraft(rawJson)) {
      val report = DraftImportReport(
        sourceFormat = "CapCut / JianYing Encrypted Draft",
        isEncryptedError = true,
        warnings = listOf(
          "Detected encrypted draft_content.json format (CapCut/JianYing post-2024.04 cipher).",
          "As documented in Xiangyu-CAS/rea-capcut, encrypted proprietary drafts cannot be parsed directly without an unencrypted JSON draft.",
        ),
      )
      return createDefaultShowcaseProject().copy(lastImportReport = report)
    }

    val root = JSONObject(rawJson)
    val warnings = mutableListOf<String>()

    // 1. Parse canvas_config
    val canvasObj = root.optJSONObject("canvas_config")
    val width = canvasObj?.optInt("width", 1920) ?: 1920
    val height = canvasObj?.optInt("height", 1080) ?: 1080
    val aspect = when {
      width > height && width.toFloat() / height.toFloat() > 2.0f -> AspectRatioPreset.RATIO_21_9
      width > height && width.toFloat() / height.toFloat() > 1.5f -> AspectRatioPreset.RATIO_16_9
      width > height -> AspectRatioPreset.RATIO_4_3
      height > width -> AspectRatioPreset.RATIO_9_16
      else -> AspectRatioPreset.RATIO_1_1
    }
    val fps = root.optDouble("fps", 30.0).toInt().coerceIn(24, 60)
    val draftName = projectNameOverride
      ?: root.optString("name").takeIf { it.isNotBlank() }
      ?: "Imported CapCut Draft"

    // 2. Parse materials lookup tables
    val materialsObj = root.optJSONObject("materials") ?: JSONObject()

    val videoMatMap = mutableMapOf<String, JSONObject>()
    materialsObj.optJSONArray("videos")?.let { arr ->
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id")
        if (id.isNotBlank()) videoMatMap[id] = item
      }
    }

    val audioMatMap = mutableMapOf<String, JSONObject>()
    materialsObj.optJSONArray("audios")?.let { arr ->
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id")
        if (id.isNotBlank()) audioMatMap[id] = item
      }
    }

    val textMatMap = mutableMapOf<String, JSONObject>()
    materialsObj.optJSONArray("texts")?.let { arr ->
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id")
        if (id.isNotBlank()) textMatMap[id] = item
      }
    }

    val effectMatMap = mutableMapOf<String, JSONObject>()
    materialsObj.optJSONArray("video_effects")?.let { arr ->
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id")
        if (id.isNotBlank()) effectMatMap[id] = item
      }
    }
    materialsObj.optJSONArray("effects")?.let { arr ->
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id")
        if (id.isNotBlank()) effectMatMap[id] = item
      }
    }

    // 3. Parse tracks & microsecond segments
    val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
    val videoClips = mutableListOf<TimelineClip>()
    val overlayClips = mutableListOf<TimelineClip>()
    val textClips = mutableListOf<TimelineClip>()
    val effectClips = mutableListOf<TimelineClip>()
    val audioClips = mutableListOf<TimelineClip>()
    val parsedSubtitles = mutableListOf<SubtitleCue>()

    var videoTrackSeen = false
    val themes = ProceduralClipTheme.entries

    for (tIdx in 0 until tracksArr.length()) {
      val trackObj = tracksArr.optJSONObject(tIdx) ?: continue
      val rawType = trackObj.optString("type", "video").lowercase()
      val segmentsArr = trackObj.optJSONArray("segments") ?: JSONArray()

      val targetTrackType = when (rawType) {
        "video" -> {
          if (!videoTrackSeen) {
            videoTrackSeen = true
            TrackType.VIDEO
          } else {
            TrackType.OVERLAY
          }
        }
        "audio" -> TrackType.AUDIO
        "text", "sticker" -> TrackType.TEXT
        "effect", "filter" -> TrackType.EFFECT
        else -> TrackType.OVERLAY
      }

      for (sIdx in 0 until segmentsArr.length()) {
        val seg = segmentsArr.optJSONObject(sIdx) ?: continue
        val matId = seg.optString("material_id", UUID.randomUUID().toString())
        val targetRange = seg.optJSONObject("target_timerange")
        val sourceRange = seg.optJSONObject("source_timerange")

        // CapCut stores timestamps in microseconds (1_000_000 = 1s)
        val startUs = targetRange?.optLong("start", 0L) ?: 0L
        val durationUs = targetRange?.optLong("duration", 3_000_000L) ?: 3_000_000L
        val sourceStartUs = sourceRange?.optLong("start", 0L) ?: 0L
        val sourceDurUs = sourceRange?.optLong("duration", durationUs) ?: durationUs

        val startMs = (startUs / 1000L).coerceAtLeast(0L)
        val durationMs = (durationUs / 1000L).coerceAtLeast(200L)
        val sourceInMs = (sourceStartUs / 1000L).coerceAtLeast(0L)
        val sourceDurationMs = (sourceDurUs / 1000L).coerceAtLeast(durationMs)

        val speed = seg.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4.0f)
        val volume = seg.optDouble("volume", 1.0).toFloat().coerceIn(0f, 2f)

        val clipSettings = seg.optJSONObject("clip")
        val scaleObj = clipSettings?.optJSONObject("scale")
        val transformObj = clipSettings?.optJSONObject("transform")
        val scaleVal = scaleObj?.optDouble("x", 1.0)?.toFloat() ?: 1.0f
        val posX = transformObj?.optDouble("x", 0.0)?.toFloat() ?: 0.0f
        val posY = transformObj?.optDouble("y", 0.0)?.toFloat() ?: 0.0f
        val rotation = clipSettings?.optDouble("rotation", 0.0)?.toFloat() ?: 0.0f
        val alpha = clipSettings?.optDouble("alpha", 1.0)?.toFloat() ?: 1.0f

        when (targetTrackType) {
          TrackType.VIDEO, TrackType.OVERLAY -> {
            val mat = videoMatMap[matId]
            val pathName = mat?.optString("path", "")?.substringAfterLast('/')?.substringAfterLast('\\')
              ?.takeIf { it.isNotBlank() }
              ?: mat?.optString("material_name")?.takeIf { it.isNotBlank() }
              ?: "CapCut Clip #${sIdx + 1}"
            if (mat?.optString("path")?.isNotBlank() == true) {
              warnings.add("Mapped local desktop path '${mat.optString("path")}' to portable studio clip '$pathName'.")
            }
            val preset = FilterPreset.entries[(sIdx + tIdx) % FilterPreset.entries.size]
            val clip = TimelineClip(
              materialId = matId,
              name = pathName,
              trackType = targetTrackType,
              timelineStartMs = startMs,
              durationMs = durationMs,
              sourceInMs = sourceInMs,
              sourceDurationMs = sourceDurationMs,
              proceduralTheme = themes[(sIdx + tIdx) % themes.size],
              speed = speed,
              volume = volume,
              scale = scaleVal,
              positionX = posX,
              positionY = posY,
              rotationDeg = rotation,
              opacity = alpha,
              colorGrading = ColorGrading(filterPreset = preset),
            )
            if (targetTrackType == TrackType.VIDEO) videoClips.add(clip) else overlayClips.add(clip)
          }
          TrackType.AUDIO -> {
            val mat = audioMatMap[matId]
            val name = mat?.optString("name")?.takeIf { it.isNotBlank() }
              ?: mat?.optString("path")?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
              ?: "CapCut Audio #${sIdx + 1}"
            audioClips.add(
              TimelineClip(
                materialId = matId,
                name = name,
                trackType = TrackType.AUDIO,
                timelineStartMs = startMs,
                durationMs = durationMs,
                sourceInMs = sourceInMs,
                sourceDurationMs = sourceDurationMs,
                speed = speed,
                volume = volume,
              )
            )
          }
          TrackType.TEXT -> {
            val mat = textMatMap[matId]
            val rawContent = mat?.optString("content", "CapCut Subtitle #${sIdx + 1}") ?: "Subtitle #${sIdx + 1}"
            val extractedText = extractTextFromCapCutContent(rawContent)
            parsedSubtitles.add(
              SubtitleCue(
                startTimeMs = startMs,
                endTimeMs = startMs + durationMs,
                text = extractedText,
                secondaryText = "",
              )
            )
            textClips.add(
              TimelineClip(
                materialId = matId,
                name = extractedText.take(22),
                trackType = TrackType.TEXT,
                timelineStartMs = startMs,
                durationMs = durationMs,
                overlayText = extractedText,
              )
            )
          }
          TrackType.EFFECT -> {
            val mat = effectMatMap[matId]
            val fxName = mat?.optString("name")?.takeIf { it.isNotBlank() } ?: "CapCut LUT FX #${sIdx + 1}"
            val mappedPreset = FilterPreset.entries.firstOrNull {
              fxName.contains(it.code, ignoreCase = true) || fxName.contains(it.displayName.take(5), ignoreCase = true)
            } ?: FilterPreset.CYBERPUNK_CYAN
            warnings.add("Approximated CapCut effect '$fxName' with ReaCut LUT '${mappedPreset.displayName}'.")
            effectClips.add(
              TimelineClip(
                materialId = matId,
                name = fxName,
                trackType = TrackType.EFFECT,
                timelineStartMs = startMs,
                durationMs = durationMs,
                colorGrading = ColorGrading(filterPreset = mappedPreset),
              )
            )
          }
        }
      }
    }

    val tracks = listOf(
      TimelineTrack(type = TrackType.VIDEO, clips = videoClips.sortedBy { it.timelineStartMs }),
      TimelineTrack(type = TrackType.OVERLAY, clips = overlayClips.sortedBy { it.timelineStartMs }),
      TimelineTrack(type = TrackType.TEXT, clips = textClips.sortedBy { it.timelineStartMs }),
      TimelineTrack(type = TrackType.EFFECT, clips = effectClips.sortedBy { it.timelineStartMs }),
      TimelineTrack(type = TrackType.AUDIO, clips = audioClips.sortedBy { it.timelineStartMs }),
    )

    val config = ProjectConfig(
      name = draftName,
      aspectRatio = aspect,
      resolution = if (height >= 1440 || width >= 2560) ResolutionPreset.RES_2K else ResolutionPreset.RES_1080P,
      fps = fps,
    )

    val tempProject = ReaCutProject(
      config = config,
      tracks = tracks,
      subtitles = parsedSubtitles.sortedBy { it.startTimeMs },
    )

    val report = DraftImportReport(
      sourceFormat = "CapCut / JianYing draft_content.json",
      isEncryptedError = false,
      importedVideoCount = videoClips.size + overlayClips.size,
      importedAudioCount = audioClips.size,
      importedSubtitleCount = parsedSubtitles.size,
      importedEffectCount = effectClips.size,
      totalTimelineDurationMs = tempProject.totalDurationMs,
      warnings = warnings.ifEmpty {
        listOf("Imported all tracks and microsecond timeranges cleanly without loss.")
      },
    )

    return tempProject.copy(lastImportReport = report)
  }

  /**
   * Exports a [ReaCutProject] into a genuine CapCut / JianYing `draft_content.json` structure
   * with microsecond (`1_000_000` per second) target/source timeranges.
   */
  fun exportToCapCutDraftJson(project: ReaCutProject): String {
    val root = JSONObject()
    root.put("id", project.config.id)
    root.put("name", project.config.name)
    root.put("version", 360000)
    root.put("fps", project.config.fps.toDouble())
    root.put("duration", project.totalDurationMs * 1000L) // microseconds

    val (w, h) = project.config.resolution.computeDimensions(project.config.aspectRatio)
    val canvasConfig = JSONObject().apply {
      put("width", w)
      put("height", h)
      put("ratio", "${project.config.aspectRatio.ratioWidth}:${project.config.aspectRatio.ratioHeight}")
    }
    root.put("canvas_config", canvasConfig)

    val videosArr = JSONArray()
    val audiosArr = JSONArray()
    val textsArr = JSONArray()
    val effectsArr = JSONArray()
    val tracksArr = JSONArray()

    for (track in project.tracks) {
      val segmentsArr = JSONArray()
      for (clip in track.clips) {
        val matId = clip.materialId
        when (track.type) {
          TrackType.VIDEO, TrackType.OVERLAY -> {
            videosArr.put(
              JSONObject().apply {
                put("id", matId)
                put("material_name", clip.name)
                put("path", clip.sourceUri ?: "reacut://procedural/${clip.proceduralTheme.name.lowercase()}.mp4")
                put("duration", clip.sourceDurationMs * 1000L)
                put("width", w)
                put("height", h)
                put("type", "video")
              }
            )
          }
          TrackType.AUDIO -> {
            audiosArr.put(
              JSONObject().apply {
                put("id", matId)
                put("name", clip.name)
                put("path", clip.sourceUri ?: "reacut://audio/${clip.name}.m4a")
                put("duration", clip.sourceDurationMs * 1000L)
                put("type", "extract_music")
              }
            )
          }
          TrackType.TEXT -> {
            textsArr.put(
              JSONObject().apply {
                put("id", matId)
                put("content", JSONObject().put("text", clip.overlayText.ifBlank { clip.name }).toString())
                put("type", "subtitle")
              }
            )
          }
          TrackType.EFFECT -> {
            effectsArr.put(
              JSONObject().apply {
                put("id", matId)
                put("name", clip.colorGrading.filterPreset.displayName)
                put("effect_id", clip.colorGrading.filterPreset.code)
                put("type", "video_effect")
              }
            )
          }
        }

        val segObj = JSONObject().apply {
          put("id", clip.id)
          put("material_id", matId)
          put("speed", clip.speed.toDouble())
          put("volume", clip.volume.toDouble())
          put(
            "target_timerange",
            JSONObject().apply {
              put("start", clip.timelineStartMs * 1000L)
              put("duration", clip.durationMs * 1000L)
            }
          )
          put(
            "source_timerange",
            JSONObject().apply {
              put("start", clip.sourceInMs * 1000L)
              put("duration", clip.durationMs * 1000L)
            }
          )
          put(
            "clip",
            JSONObject().apply {
              put("alpha", clip.opacity.toDouble())
              put("rotation", clip.rotationDeg.toDouble())
              put("scale", JSONObject().put("x", clip.scale.toDouble()).put("y", clip.scale.toDouble()))
              put("transform", JSONObject().put("x", clip.positionX.toDouble()).put("y", clip.positionY.toDouble()))
            }
          )
        }
        segmentsArr.put(segObj)
      }

      val capCutTrackType = when (track.type) {
        TrackType.VIDEO, TrackType.OVERLAY -> "video"
        TrackType.AUDIO -> "audio"
        TrackType.TEXT -> "text"
        TrackType.EFFECT -> "effect"
      }
      tracksArr.put(
        JSONObject().apply {
          put("id", track.id)
          put("type", capCutTrackType)
          put("name", track.name)
          put("segments", segmentsArr)
        }
      )
    }

    // Also export standalone subtitles into a CapCut text track if not already duplicated
    if (project.subtitles.isNotEmpty()) {
      val subSegments = JSONArray()
      for (cue in project.subtitles) {
        val matId = "sub_${cue.id}"
        textsArr.put(
          JSONObject().apply {
            put("id", matId)
            put(
              "content",
              JSONObject().apply {
                put("text", if (cue.secondaryText.isNotBlank()) "${cue.text}\n${cue.secondaryText}" else cue.text)
                put("font_size", cue.style.fontSizeSp.toDouble())
              }.toString()
            )
            put("type", "subtitle")
          }
        )
        subSegments.put(
          JSONObject().apply {
            put("id", cue.id)
            put("material_id", matId)
            put(
              "target_timerange",
              JSONObject().apply {
                put("start", cue.startTimeMs * 1000L)
                put("duration", cue.durationMs * 1000L)
              }
            )
          }
        )
      }
      tracksArr.put(
        JSONObject().apply {
          put("id", "track_subtitles_exported")
          put("type", "text")
          put("segments", subSegments)
        }
      )
    }

    root.put(
      "materials",
      JSONObject().apply {
        put("videos", videosArr)
        put("audios", audiosArr)
        put("texts", textsArr)
        put("video_effects", effectsArr)
      }
    )
    root.put("tracks", tracksArr)
    return root.toString(2)
  }

  /**
   * Serializes a full [ReaCutProject] lossless state into JSON (used by Room persistence & .reacut.json export).
   */
  fun serializeReaCutProject(project: ReaCutProject): String {
    val root = JSONObject()
    val cfg = project.config
    root.put(
      "config",
      JSONObject().apply {
        put("id", cfg.id)
        put("name", cfg.name)
        put("aspectRatio", cfg.aspectRatio.name)
        put("resolution", cfg.resolution.name)
        put("fps", cfg.fps)
        put("canvasColorHex", cfg.canvasColorHex)
        put("createdAt", cfg.createdAt)
        put("updatedAt", cfg.updatedAt)
      }
    )

    val tracksArr = JSONArray()
    for (track in project.tracks) {
      val clipsArr = JSONArray()
      for (clip in track.clips) {
        val kfArr = JSONArray()
        for (kf in clip.keyframes) {
          kfArr.put(
            JSONObject().apply {
              put("id", kf.id)
              put("offsetMs", kf.offsetMs)
              put("scale", kf.scale.toDouble())
              put("positionX", kf.positionX.toDouble())
              put("positionY", kf.positionY.toDouble())
              put("rotationDeg", kf.rotationDeg.toDouble())
              put("opacity", kf.opacity.toDouble())
              put("easing", kf.easing.name)
            }
          )
        }
        clipsArr.put(
          JSONObject().apply {
            put("id", clip.id)
            put("materialId", clip.materialId)
            put("name", clip.name)
            put("trackType", clip.trackType.name)
            put("timelineStartMs", clip.timelineStartMs)
            put("durationMs", clip.durationMs)
            put("sourceInMs", clip.sourceInMs)
            put("sourceDurationMs", clip.sourceDurationMs)
            put("sourceUri", clip.sourceUri ?: "")
            put("proceduralTheme", clip.proceduralTheme.name)
            put("overlayText", clip.overlayText)
            put("speed", clip.speed.toDouble())
            put("isReversed", clip.isReversed)
            put("isFrozen", clip.isFrozen)
            put("scale", clip.scale.toDouble())
            put("positionX", clip.positionX.toDouble())
            put("positionY", clip.positionY.toDouble())
            put("rotationDeg", clip.rotationDeg.toDouble())
            put("opacity", clip.opacity.toDouble())
            put("mirrorHorizontal", clip.mirrorHorizontal)
            put("mirrorVertical", clip.mirrorVertical)
            put("volume", clip.volume.toDouble())
            put("fadeInMs", clip.fadeInMs)
            put("fadeOutMs", clip.fadeOutMs)
            put(
              "colorGrading",
              JSONObject().apply {
                put("brightness", clip.colorGrading.brightness.toDouble())
                put("contrast", clip.colorGrading.contrast.toDouble())
                put("saturation", clip.colorGrading.saturation.toDouble())
                put("temperature", clip.colorGrading.temperature.toDouble())
                put("vignette", clip.colorGrading.vignette.toDouble())
                put("sharpness", clip.colorGrading.sharpness.toDouble())
                put("filterPreset", clip.colorGrading.filterPreset.name)
              }
            )
            put(
              "transition",
              JSONObject().apply {
                put("type", clip.transition.type.name)
                put("durationMs", clip.transition.durationMs)
              }
            )
            put("keyframes", kfArr)
          }
        )
      }
      tracksArr.put(
        JSONObject().apply {
          put("id", track.id)
          put("type", track.type.name)
          put("name", track.name)
          put("isMuted", track.isMuted)
          put("isLocked", track.isLocked)
          put("isHidden", track.isHidden)
          put("clips", clipsArr)
        }
      )
    }
    root.put("tracks", tracksArr)

    val subsArr = JSONArray()
    for (sub in project.subtitles) {
      subsArr.put(
        JSONObject().apply {
          put("id", sub.id)
          put("startTimeMs", sub.startTimeMs)
          put("endTimeMs", sub.endTimeMs)
          put("text", sub.text)
          put("secondaryText", sub.secondaryText)
          put("speaker", sub.speaker)
          put(
            "style",
            JSONObject().apply {
              put("fontSizeSp", sub.style.fontSizeSp.toDouble())
              put("textColorHex", sub.style.textColorHex)
              put("strokeColorHex", sub.style.strokeColorHex)
              put("strokeWidth", sub.style.strokeWidth.toDouble())
              put("bgColorHex", sub.style.bgColorHex)
              put("bgOpacity", sub.style.bgOpacity.toDouble())
              put("verticalPosition", sub.style.verticalPosition.toDouble())
              put("isBold", sub.style.isBold)
              put("isItalic", sub.style.isItalic)
              put("showBilingual", sub.style.showBilingual)
              put("animation", sub.style.animation.name)
            }
          )
        }
      )
    }
    root.put("subtitles", subsArr)

    val markersArr = JSONArray()
    for (m in project.markers) {
      markersArr.put(
        JSONObject().apply {
          put("id", m.id)
          put("timeMs", m.timeMs)
          put("label", m.label)
          put("colorHex", m.colorHex)
        }
      )
    }
    root.put("markers", markersArr)
    return root.toString()
  }

  /**
   * Deserializes a lossless [ReaCutProject] JSON string.
   */
  fun deserializeReaCutProject(jsonStr: String): ReaCutProject {
    return try {
      val root = JSONObject(jsonStr)
      val cfgObj = root.optJSONObject("config") ?: JSONObject()
      val config = ProjectConfig(
        id = cfgObj.optString("id", UUID.randomUUID().toString()),
        name = cfgObj.optString("name", "ReaCut Project"),
        aspectRatio = runCatching {
          AspectRatioPreset.valueOf(cfgObj.optString("aspectRatio", AspectRatioPreset.RATIO_16_9.name))
        }.getOrDefault(AspectRatioPreset.RATIO_16_9),
        resolution = runCatching {
          ResolutionPreset.valueOf(cfgObj.optString("resolution", ResolutionPreset.RES_1080P.name))
        }.getOrDefault(ResolutionPreset.RES_1080P),
        fps = cfgObj.optInt("fps", 30),
        canvasColorHex = cfgObj.optLong("canvasColorHex", 0xFF090D14),
        createdAt = cfgObj.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = cfgObj.optLong("updatedAt", System.currentTimeMillis()),
      )

      val tracks = mutableListOf<TimelineTrack>()
      val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
      for (i in 0 until tracksArr.length()) {
        val tObj = tracksArr.optJSONObject(i) ?: continue
        val tType = runCatching {
          TrackType.valueOf(tObj.optString("type", TrackType.VIDEO.name))
        }.getOrDefault(TrackType.VIDEO)

        val clips = mutableListOf<TimelineClip>()
        val clipsArr = tObj.optJSONArray("clips") ?: JSONArray()
        for (j in 0 until clipsArr.length()) {
          val cObj = clipsArr.optJSONObject(j) ?: continue
          val cgObj = cObj.optJSONObject("colorGrading")
          val trObj = cObj.optJSONObject("transition")
          val kfArr = cObj.optJSONArray("keyframes") ?: JSONArray()
          val kfs = mutableListOf<ClipKeyframe>()
          for (k in 0 until kfArr.length()) {
            val kObj = kfArr.optJSONObject(k) ?: continue
            kfs.add(
              ClipKeyframe(
                id = kObj.optString("id", UUID.randomUUID().toString()),
                offsetMs = kObj.optLong("offsetMs", 0L),
                scale = kObj.optDouble("scale", 1.0).toFloat(),
                positionX = kObj.optDouble("positionX", 0.0).toFloat(),
                positionY = kObj.optDouble("positionY", 0.0).toFloat(),
                rotationDeg = kObj.optDouble("rotationDeg", 0.0).toFloat(),
                opacity = kObj.optDouble("opacity", 1.0).toFloat(),
                easing = runCatching {
                  EasingType.valueOf(kObj.optString("easing", EasingType.EASE_IN_OUT.name))
                }.getOrDefault(EasingType.EASE_IN_OUT),
              )
            )
          }

          clips.add(
            TimelineClip(
              id = cObj.optString("id", UUID.randomUUID().toString()),
              materialId = cObj.optString("materialId", UUID.randomUUID().toString()),
              name = cObj.optString("name", "Clip"),
              trackType = runCatching {
                TrackType.valueOf(cObj.optString("trackType", tType.name))
              }.getOrDefault(tType),
              timelineStartMs = cObj.optLong("timelineStartMs", 0L),
              durationMs = cObj.optLong("durationMs", 3000L),
              sourceInMs = cObj.optLong("sourceInMs", 0L),
              sourceDurationMs = cObj.optLong("sourceDurationMs", 15000L),
              sourceUri = cObj.optString("sourceUri").takeIf { it.isNotBlank() },
              proceduralTheme = runCatching {
                ProceduralClipTheme.valueOf(cObj.optString("proceduralTheme", ProceduralClipTheme.CYBERPUNK_GRID.name))
              }.getOrDefault(ProceduralClipTheme.CYBERPUNK_GRID),
              overlayText = cObj.optString("overlayText", ""),
              speed = cObj.optDouble("speed", 1.0).toFloat(),
              isReversed = cObj.optBoolean("isReversed", false),
              isFrozen = cObj.optBoolean("isFrozen", false),
              scale = cObj.optDouble("scale", 1.0).toFloat(),
              positionX = cObj.optDouble("positionX", 0.0).toFloat(),
              positionY = cObj.optDouble("positionY", 0.0).toFloat(),
              rotationDeg = cObj.optDouble("rotationDeg", 0.0).toFloat(),
              opacity = cObj.optDouble("opacity", 1.0).toFloat(),
              mirrorHorizontal = cObj.optBoolean("mirrorHorizontal", false),
              mirrorVertical = cObj.optBoolean("mirrorVertical", false),
              volume = cObj.optDouble("volume", 1.0).toFloat(),
              fadeInMs = cObj.optLong("fadeInMs", 0L),
              fadeOutMs = cObj.optLong("fadeOutMs", 0L),
              colorGrading = ColorGrading(
                brightness = cgObj?.optDouble("brightness", 0.0)?.toFloat() ?: 0f,
                contrast = cgObj?.optDouble("contrast", 1.0)?.toFloat() ?: 1f,
                saturation = cgObj?.optDouble("saturation", 1.0)?.toFloat() ?: 1f,
                temperature = cgObj?.optDouble("temperature", 0.0)?.toFloat() ?: 0f,
                vignette = cgObj?.optDouble("vignette", 0.0)?.toFloat() ?: 0f,
                sharpness = cgObj?.optDouble("sharpness", 0.0)?.toFloat() ?: 0f,
                filterPreset = runCatching {
                  FilterPreset.valueOf(cgObj?.optString("filterPreset") ?: FilterPreset.NONE.name)
                }.getOrDefault(FilterPreset.NONE),
              ),
              transition = TransitionConfig(
                type = runCatching {
                  TransitionType.valueOf(trObj?.optString("type") ?: TransitionType.NONE.name)
                }.getOrDefault(TransitionType.NONE),
                durationMs = trObj?.optLong("durationMs", 600L) ?: 600L,
              ),
              keyframes = kfs,
            )
          )
        }

        tracks.add(
          TimelineTrack(
            id = tObj.optString("id", UUID.randomUUID().toString()),
            type = tType,
            name = tObj.optString("name", tType.displayName),
            isMuted = tObj.optBoolean("isMuted", false),
            isLocked = tObj.optBoolean("isLocked", false),
            isHidden = tObj.optBoolean("isHidden", false),
            clips = clips,
          )
        )
      }

      val subtitles = mutableListOf<SubtitleCue>()
      val subsArr = root.optJSONArray("subtitles") ?: JSONArray()
      for (i in 0 until subsArr.length()) {
        val sObj = subsArr.optJSONObject(i) ?: continue
        val stObj = sObj.optJSONObject("style")
        subtitles.add(
          SubtitleCue(
            id = sObj.optString("id", UUID.randomUUID().toString()),
            startTimeMs = sObj.optLong("startTimeMs", 0L),
            endTimeMs = sObj.optLong("endTimeMs", 2000L),
            text = sObj.optString("text", ""),
            secondaryText = sObj.optString("secondaryText", ""),
            speaker = sObj.optString("speaker", ""),
            style = SubtitleStyle(
              fontSizeSp = stObj?.optDouble("fontSizeSp", 18.0)?.toFloat() ?: 18f,
              textColorHex = stObj?.optLong("textColorHex", 0xFFFFFFFF) ?: 0xFFFFFFFF,
              strokeColorHex = stObj?.optLong("strokeColorHex", 0xFF000000) ?: 0xFF000000,
              strokeWidth = stObj?.optDouble("strokeWidth", 3.0)?.toFloat() ?: 3f,
              bgColorHex = stObj?.optLong("bgColorHex", 0xFF0D1117) ?: 0xFF0D1117,
              bgOpacity = stObj?.optDouble("bgOpacity", 0.65)?.toFloat() ?: 0.65f,
              verticalPosition = stObj?.optDouble("verticalPosition", 0.84)?.toFloat() ?: 0.84f,
              isBold = stObj?.optBoolean("isBold", true) ?: true,
              isItalic = stObj?.optBoolean("isItalic", false) ?: false,
              showBilingual = stObj?.optBoolean("showBilingual", true) ?: true,
              animation = runCatching {
                SubtitleAnimation.valueOf(stObj?.optString("animation") ?: SubtitleAnimation.POP_IN.name)
              }.getOrDefault(SubtitleAnimation.POP_IN),
            ),
          )
        )
      }

      val markers = mutableListOf<TimelineMarker>()
      val markersArr = root.optJSONArray("markers") ?: JSONArray()
      for (i in 0 until markersArr.length()) {
        val mObj = markersArr.optJSONObject(i) ?: continue
        markers.add(
          TimelineMarker(
            id = mObj.optString("id", UUID.randomUUID().toString()),
            timeMs = mObj.optLong("timeMs", 0L),
            label = mObj.optString("label", "Marker"),
            colorHex = mObj.optLong("colorHex", 0xFF00E5FF),
          )
        )
      }

      ReaCutProject(
        config = config,
        tracks = tracks,
        subtitles = subtitles,
        markers = markers,
      )
    } catch (e: Exception) {
      createDefaultShowcaseProject()
    }
  }

  /**
   * Generates a sample CapCut Desktop `draft_content.json` for instant testing of the Draft Importer.
   */
  fun generateSampleCapCutDesktopDraftJson(): String {
    return """
      {
        "id": "capcut-draft-rea-research-01",
        "name": "CapCut Desktop Imported Reel",
        "version": 360000,
        "fps": 30.0,
        "duration": 9000000,
        "canvas_config": {
          "width": 1920,
          "height": 1080,
          "ratio": "16:9"
        },
        "materials": {
          "videos": [
            {
              "id": "mat_vid_1",
              "material_name": "intro_cyber_grid.mp4",
              "path": "/Users/xiangyu/Movies/CapCut/Projects/intro_cyber_grid.mp4",
              "duration": 3500000,
              "width": 1920,
              "height": 1080,
              "type": "video"
            },
            {
              "id": "mat_vid_2",
              "material_name": "neon_shibuya_broll.mp4",
              "path": "/Users/xiangyu/Movies/CapCut/Projects/neon_shibuya_broll.mp4",
              "duration": 3500000,
              "width": 1920,
              "height": 1080,
              "type": "video"
            },
            {
              "id": "mat_vid_3",
              "material_name": "outro_sunset_drive.mp4",
              "path": "/Users/xiangyu/Movies/CapCut/Projects/outro_sunset_drive.mp4",
              "duration": 2000000,
              "width": 1920,
              "height": 1080,
              "type": "video"
            }
          ],
          "audios": [
            {
              "id": "mat_aud_1",
              "name": "Synthwave_120BPM_Master.wav",
              "path": "/Users/xiangyu/Music/Synthwave_120BPM_Master.wav",
              "duration": 9000000,
              "type": "extract_music"
            }
          ],
          "texts": [
            {
              "id": "mat_txt_1",
              "content": "{\"text\":\"ReaCut Draft Importer: 1:1 Microsecond Timeline\"}",
              "type": "subtitle"
            },
            {
              "id": "mat_txt_2",
              "content": "{\"text\":\"Decoded from CapCut draft_content.json\"}",
              "type": "subtitle"
            },
            {
              "id": "mat_txt_3",
              "content": "{\"text\":\"Export to H.264 MP4 or SubRip .SRT\"}",
              "type": "subtitle"
            }
          ],
          "video_effects": [
            {
              "id": "mat_fx_1",
              "name": "Cyberpunk Cyan LUT",
              "effect_id": "cyber_cyan",
              "type": "video_effect"
            }
          ]
        },
        "tracks": [
          {
            "id": "track_main_v1",
            "type": "video",
            "segments": [
              {
                "id": "seg_v1",
                "material_id": "mat_vid_1",
                "speed": 1.0,
                "volume": 1.0,
                "target_timerange": { "start": 0, "duration": 3500000 },
                "source_timerange": { "start": 0, "duration": 3500000 },
                "clip": {
                  "alpha": 1.0,
                  "rotation": 0.0,
                  "scale": { "x": 1.0, "y": 1.0 },
                  "transform": { "x": 0.0, "y": 0.0 }
                }
              },
              {
                "id": "seg_v2",
                "material_id": "mat_vid_2",
                "speed": 1.15,
                "volume": 0.9,
                "target_timerange": { "start": 3500000, "duration": 3500000 },
                "source_timerange": { "start": 200000, "duration": 3500000 },
                "clip": {
                  "alpha": 1.0,
                  "rotation": 0.0,
                  "scale": { "x": 1.08, "y": 1.08 },
                  "transform": { "x": 0.0, "y": 0.0 }
                }
              },
              {
                "id": "seg_v3",
                "material_id": "mat_vid_3",
                "speed": 1.0,
                "volume": 1.0,
                "target_timerange": { "start": 7000000, "duration": 2000000 },
                "source_timerange": { "start": 0, "duration": 2000000 },
                "clip": {
                  "alpha": 1.0,
                  "rotation": 0.0,
                  "scale": { "x": 1.0, "y": 1.0 },
                  "transform": { "x": 0.0, "y": 0.0 }
                }
              }
            ]
          },
          {
            "id": "track_fx_1",
            "type": "effect",
            "segments": [
              {
                "id": "seg_fx1",
                "material_id": "mat_fx_1",
                "target_timerange": { "start": 1000000, "duration": 5000000 }
              }
            ]
          },
          {
            "id": "track_text_1",
            "type": "text",
            "segments": [
              {
                "id": "seg_t1",
                "material_id": "mat_txt_1",
                "target_timerange": { "start": 200000, "duration": 3000000 }
              },
              {
                "id": "seg_t2",
                "material_id": "mat_txt_2",
                "target_timerange": { "start": 3600000, "duration": 3200000 }
              },
              {
                "id": "seg_t3",
                "material_id": "mat_txt_3",
                "target_timerange": { "start": 7000000, "duration": 1900000 }
              }
            ]
          },
          {
            "id": "track_audio_1",
            "type": "audio",
            "segments": [
              {
                "id": "seg_a1",
                "material_id": "mat_aud_1",
                "volume": 0.85,
                "target_timerange": { "start": 0, "duration": 9000000 },
                "source_timerange": { "start": 0, "duration": 9000000 }
              }
            ]
          }
        ]
      }
    """.trimIndent()
  }

  /**
   * Builds the default multi-track ReaCut Studio Showcase project preloaded on first launch.
   */
  fun createDefaultShowcaseProject(): ReaCutProject {
    val clip1 = TimelineClip(
      id = "clip_v1_intro",
      materialId = "mat_v1_intro",
      name = "01_Cyberpunk_Grid_Intro.mp4",
      trackType = TrackType.VIDEO,
      timelineStartMs = 0L,
      durationMs = 3_200L,
      sourceInMs = 0L,
      sourceDurationMs = 10_000L,
      proceduralTheme = ProceduralClipTheme.CYBERPUNK_GRID,
      colorGrading = ColorGrading(
        contrast = 1.12f,
        saturation = 1.18f,
        filterPreset = FilterPreset.CYBERPUNK_CYAN,
      ),
      transition = TransitionConfig(TransitionType.ZOOM_IN, 500L),
      keyframes = listOf(
        ClipKeyframe(offsetMs = 0L, scale = 1.0f, positionX = 0f, positionY = 0f, rotationDeg = 0f, opacity = 1f),
        ClipKeyframe(offsetMs = 3_000L, scale = 1.16f, positionX = 0.04f, positionY = -0.02f, rotationDeg = 0f, opacity = 1f),
      ),
    )

    val clip2 = TimelineClip(
      id = "clip_v1_city",
      materialId = "mat_v1_city",
      name = "02_Neon_Horizon_Scene.mp4",
      trackType = TrackType.VIDEO,
      timelineStartMs = 3_200L,
      durationMs = 3_400L,
      sourceInMs = 500L,
      sourceDurationMs = 12_000L,
      proceduralTheme = ProceduralClipTheme.NEON_CITY,
      colorGrading = ColorGrading(
        contrast = 1.15f,
        saturation = 1.1f,
        temperature = 0.12f,
        filterPreset = FilterPreset.TEAL_ORANGE,
      ),
      transition = TransitionConfig(TransitionType.CROSS_DISSOLVE, 600L),
    )

    val clip3 = TimelineClip(
      id = "clip_v1_outro",
      materialId = "mat_v1_outro",
      name = "03_Sunset_Synth_Outro.mp4",
      trackType = TrackType.VIDEO,
      timelineStartMs = 6_600L,
      durationMs = 2_800L,
      sourceInMs = 0L,
      sourceDurationMs = 8_000L,
      proceduralTheme = ProceduralClipTheme.SUNSET_HORIZON,
      colorGrading = ColorGrading(
        brightness = 0.03f,
        contrast = 1.08f,
        vignette = 0.25f,
        filterPreset = FilterPreset.WARM_SUNSET,
      ),
      transition = TransitionConfig(TransitionType.SLIDE_LEFT, 500L),
    )

    val pipClip = TimelineClip(
      id = "clip_v2_pip",
      materialId = "mat_v2_pip",
      name = "PIP_Spectrum_Meter.mp4",
      trackType = TrackType.OVERLAY,
      timelineStartMs = 1_400L,
      durationMs = 3_600L,
      proceduralTheme = ProceduralClipTheme.WAVEFORM_SYNTH,
      scale = 0.36f,
      positionX = 0.52f,
      positionY = -0.48f,
      opacity = 0.92f,
      transition = TransitionConfig(TransitionType.GLITCH_FLASH, 400L),
    )

    val fxClip = TimelineClip(
      id = "clip_fx_lut",
      materialId = "mat_fx_lut",
      name = "Teal & Orange Master LUT",
      trackType = TrackType.EFFECT,
      timelineStartMs = 2_800L,
      durationMs = 4_000L,
      colorGrading = ColorGrading(filterPreset = FilterPreset.TEAL_ORANGE),
    )

    val audioClip = TimelineClip(
      id = "clip_a1_bgm",
      materialId = "mat_a1_bgm",
      name = "ReaCut_Studio_Drive_124BPM.wav",
      trackType = TrackType.AUDIO,
      timelineStartMs = 0L,
      durationMs = 9_400L,
      volume = 0.9f,
      fadeInMs = 400L,
      fadeOutMs = 600L,
    )

    val subtitles = listOf(
      SubtitleCue(
        id = "sub_1",
        startTimeMs = 200L,
        endTimeMs = 3_100L,
        text = "ReaCut Open-Source NLE Studio on Android",
        secondaryText = "محرر الفيديو المفتوح المصدر ReaCut على أندرويد",
      ),
      SubtitleCue(
        id = "sub_2",
        startTimeMs = 3_300L,
        endTimeMs = 6_500L,
        text = "Multi-Track Timeline • Keyframes • CapCut Draft I/O",
        secondaryText = "خط زمني متعدد المسارات • إطارات مفتاحية • مشاريع CapCut",
      ),
      SubtitleCue(
        id = "sub_3",
        startTimeMs = 6_700L,
        endTimeMs = 9_300L,
        text = "Hardware MediaCodec H.264 MP4 & SRT Export",
        secondaryText = "تصدير حقيقي للفيديو بصيغة MP4 والترجمة SRT",
      ),
    )

    val textClips = subtitles.map { cue ->
      TimelineClip(
        id = "tclip_${cue.id}",
        materialId = "mat_${cue.id}",
        name = cue.text.take(20),
        trackType = TrackType.TEXT,
        timelineStartMs = cue.startTimeMs,
        durationMs = cue.durationMs,
        overlayText = cue.text,
      )
    }

    return ReaCutProject(
      config = ProjectConfig(
        id = "project_reacut_showcase",
        name = "ReaCut Studio Showcase",
        aspectRatio = AspectRatioPreset.RATIO_16_9,
        resolution = ResolutionPreset.RES_1080P,
        fps = 30,
      ),
      tracks = listOf(
        TimelineTrack(id = "track_v1", type = TrackType.VIDEO, clips = listOf(clip1, clip2, clip3)),
        TimelineTrack(id = "track_v2", type = TrackType.OVERLAY, clips = listOf(pipClip)),
        TimelineTrack(id = "track_t1", type = TrackType.TEXT, clips = textClips),
        TimelineTrack(id = "track_fx", type = TrackType.EFFECT, clips = listOf(fxClip)),
        TimelineTrack(id = "track_a1", type = TrackType.AUDIO, clips = listOf(audioClip)),
      ),
      subtitles = subtitles,
      markers = listOf(
        TimelineMarker(timeMs = 0L, label = "Intro Start", colorHex = 0xFF00E5FF),
        TimelineMarker(timeMs = 3_200L, label = "Beat Drop / Scene 2", colorHex = 0xFFFFAB00),
        TimelineMarker(timeMs = 6_600L, label = "Outro Transition", colorHex = 0xFF1DE9B6),
      ),
    )
  }

  /**
   * Builds a second pre-configured vertical 9:16 project for testing project switching.
   */
  fun createVerticalReelProject(): ReaCutProject {
    val clip1 = TimelineClip(
      id = "clip_reel_1",
      name = "Vertical_Matrix_Stream.mp4",
      trackType = TrackType.VIDEO,
      timelineStartMs = 0L,
      durationMs = 3_000L,
      proceduralTheme = ProceduralClipTheme.MATRIX_STREAM,
      colorGrading = ColorGrading(filterPreset = FilterPreset.MATRIX_GREEN, contrast = 1.2f),
      transition = TransitionConfig(TransitionType.GLITCH_FLASH, 450L),
    )
    val clip2 = TimelineClip(
      id = "clip_reel_2",
      name = "SMPTE_Studio_Countdown.mp4",
      trackType = TrackType.VIDEO,
      timelineStartMs = 3_000L,
      durationMs = 3_000L,
      proceduralTheme = ProceduralClipTheme.STUDIO_COUNTDOWN,
      colorGrading = ColorGrading(filterPreset = FilterPreset.HIGH_CONTRAST),
      transition = TransitionConfig(TransitionType.ZOOM_IN, 500L),
    )
    val subs = listOf(
      SubtitleCue(
        startTimeMs = 150L,
        endTimeMs = 2_900L,
        text = "9:16 Vertical Short-Form Reel",
        secondaryText = "تصميم فيديو عمودي 9:16",
      ),
      SubtitleCue(
        startTimeMs = 3_050L,
        endTimeMs = 5_900L,
        text = "100% Touch-Ready NLE Controls",
        secondaryText = "تحكم كامل باللمس",
      ),
    )
    return ReaCutProject(
      config = ProjectConfig(
        id = "project_vertical_reel",
        name = "Cyberpunk Short Reel (9:16)",
        aspectRatio = AspectRatioPreset.RATIO_9_16,
        resolution = ResolutionPreset.RES_720P,
        fps = 30,
      ),
      tracks = listOf(
        TimelineTrack(type = TrackType.VIDEO, clips = listOf(clip1, clip2)),
        TimelineTrack(type = TrackType.OVERLAY, clips = emptyList()),
        TimelineTrack(
          type = TrackType.TEXT,
          clips = subs.map {
            TimelineClip(
              name = it.text.take(18),
              trackType = TrackType.TEXT,
              timelineStartMs = it.startTimeMs,
              durationMs = it.durationMs,
              overlayText = it.text,
            )
          }
        ),
        TimelineTrack(type = TrackType.EFFECT, clips = emptyList()),
        TimelineTrack(
          type = TrackType.AUDIO,
          clips = listOf(
            TimelineClip(
              name = "Bassline_Pulse_Loop.m4a",
              trackType = TrackType.AUDIO,
              timelineStartMs = 0L,
              durationMs = 6_000L,
            )
          )
        ),
      ),
      subtitles = subs,
      markers = listOf(TimelineMarker(timeMs = 3_000L, label = "Scene Cut", colorHex = 0xFFFF4081)),
    )
  }

  private fun extractTextFromCapCutContent(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("{")) {
      return runCatching {
        val obj = JSONObject(trimmed)
        obj.optString("text").takeIf { it.isNotBlank() } ?: trimmed
      }.getOrDefault(trimmed)
    }
    return trimmed
  }
}
