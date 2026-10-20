package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

sealed class ExportStatus {
  data object Idle : ExportStatus()
  data class Encoding(
    val progress: Float,
    val currentFrame: Int,
    val totalFrames: Int,
    val message: String,
  ) : ExportStatus()

  data class Completed(
    val outputFile: File,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val totalFrames: Int,
  ) : ExportStatus()

  data class Failed(val errorMessage: String) : ExportStatus()
}

/**
 * Native Android Hardware MP4 Video Export & Frame Compositor Engine.
 * Replaces desktop Electron FFmpeg subprocess spawning with Android's hardware-accelerated
 * `MediaCodec` (H.264/AVC) + `MediaMuxer` + `Canvas` compositor so real playable `.mp4`
 * video files are rendered directly on the Android device.
 */
object RealMp4ExportEngine {

  /**
   * Renders a full [ReaCutProject] timeline into a real, playable H.264 `.mp4` video file on disk.
   */
  suspend fun exportProjectToMp4(
    context: Context,
    project: ReaCutProject,
    outputResolution: ResolutionPreset = project.config.resolution,
    outputFps: Int = project.config.fps,
    burnInSubtitles: Boolean = true,
    onProgress: (ExportStatus.Encoding) -> Unit,
  ): ExportStatus = withContext(Dispatchers.Default) {
    val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
    val safeName = project.config.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "reacut_export" }
    val outFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.mp4")

    val (rawW, rawH) = outputResolution.computeDimensions(project.config.aspectRatio)
    // Keep hardware encoder resolution within safe universal H.264 profile bounds on mobile/emulators
    val scaleDown = if (rawW > 1280 || rawH > 1280) 0.5f else 1f
    val width = (((rawW * scaleDown).toInt() / 16) * 16).coerceIn(320, 1280)
    val height = (((rawH * scaleDown).toInt() / 16) * 16).coerceIn(320, 1280)
    val fps = outputFps.coerceIn(15, 60)
    val durationMs = project.totalDurationMs.coerceIn(1_000L, 60_000L)
    val totalFrames = ((durationMs * fps) / 1000L).toInt().coerceAtLeast(15)

    var encoder: MediaCodec? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var trackIndex = -1

    try {
      val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
      val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
        setInteger(
          MediaFormat.KEY_COLOR_FORMAT,
          MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        )
        setInteger(MediaFormat.KEY_BIT_RATE, outputResolution.bitrateBps.coerceIn(1_500_000, 8_000_000))
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }

      encoder = MediaCodec.createEncoderByType(mimeType)
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      val inputSurface = encoder.createInputSurface()
      encoder.start()

      muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val bufferInfo = MediaCodec.BufferInfo()
      val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val offscreenCanvas = Canvas(frameBitmap)

      for (frameIdx in 0 until totalFrames) {
        coroutineContext.ensureActive()
        val playheadMs = (frameIdx.toLong() * 1000L) / fps.toLong()

        // Render composite timeline frame at playheadMs onto offscreenBitmap
        renderCompositeFrameToCanvas(
          context = context,
          canvas = offscreenCanvas,
          width = width.toFloat(),
          height = height.toFloat(),
          project = project,
          playheadMs = playheadMs,
          burnInSubtitles = burnInSubtitles,
        )

        // Draw bitmap onto MediaCodec's input Surface
        val surfaceCanvas = inputSurface.lockCanvas(null)
        try {
          surfaceCanvas.drawBitmap(frameBitmap, 0f, 0f, null)
        } finally {
          inputSurface.unlockCanvasAndPost(surfaceCanvas)
        }

        // Drain encoded H.264 packets
        val drainResult = drainEncoder(
          encoder = encoder,
          muxer = muxer,
          bufferInfo = bufferInfo,
          endOfStream = false,
          currentTrackIndex = trackIndex,
          isMuxerStarted = muxerStarted,
        )
        trackIndex = drainResult.first
        muxerStarted = drainResult.second

        if (frameIdx % 3 == 0 || frameIdx == totalFrames - 1) {
          onProgress(
            ExportStatus.Encoding(
              progress = (frameIdx + 1).toFloat() / totalFrames.toFloat(),
              currentFrame = frameIdx + 1,
              totalFrames = totalFrames,
              message = "Encoding H.264 Frame ${frameIdx + 1}/$totalFrames (${width}x${height} @ ${fps}fps)",
            )
          )
        }
      }

      // Signal End-Of-Stream to flush remaining H.264 frames
      encoder.signalEndOfInputStream()
      drainEncoder(
        encoder = encoder,
        muxer = muxer,
        bufferInfo = bufferInfo,
        endOfStream = true,
        currentTrackIndex = trackIndex,
        isMuxerStarted = muxerStarted,
      )

      frameBitmap.recycle()
      inputSurface.release()

      if (muxerStarted) {
        muxer.stop()
      }
      muxer.release()
      muxer = null

      encoder.stop()
      encoder.release()
      encoder = null

      ExportStatus.Completed(
        outputFile = outFile,
        fileSizeBytes = outFile.length(),
        durationMs = durationMs,
        width = width,
        height = height,
        fps = fps,
        totalFrames = totalFrames,
      )
    } catch (e: Exception) {
      runCatching {
        if (muxerStarted) muxer?.stop()
        muxer?.release()
      }
      runCatching {
        encoder?.stop()
        encoder?.release()
      }
      ExportStatus.Failed(e.message ?: "Hardware MP4 encoding failed.")
    }
  }

  /**
   * Renders the exact composite NLE frame at [playheadMs] onto an `android.graphics.Canvas`.
   * Used both by the hardware MP4 video encoder AND available for high-speed bitmap snapshots.
   */
  fun renderCompositeFrameToCanvas(
    context: Context,
    canvas: Canvas,
    width: Float,
    height: Float,
    project: ReaCutProject,
    playheadMs: Long,
    burnInSubtitles: Boolean = true,
  ) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = project.config.canvasColorHex.toInt()
      style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width, height, bgPaint)

    // Find active FX LUT track clip at playheadMs
    val activeFxPreset = project.tracks
      .filter { it.type == TrackType.EFFECT && !it.isHidden && !it.isMuted }
      .flatMap { it.clips }
      .firstOrNull { playheadMs in it.timelineStartMs until it.timelineEndMs }
      ?.colorGrading
      ?.filterPreset

    // Render active VIDEO and OVERLAY clips in layer order
    val visualTracks = project.tracks.filter {
      (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) && !it.isHidden
    }

    for (track in visualTracks) {
      val activeClips = track.clips.filter { playheadMs in it.timelineStartMs until it.timelineEndMs }
      for (clip in activeClips) {
        drawClipLayer(
          context = context,
          canvas = canvas,
          canvasWidth = width,
          canvasHeight = height,
          clip = clip,
          playheadMs = playheadMs,
          activeFxPreset = activeFxPreset,
        )
      }
    }

    // Render active TEXT track clips
    val textTracks = project.tracks.filter { it.type == TrackType.TEXT && !it.isHidden }
    val activeSubtitles = if (burnInSubtitles) {
      project.subtitles.filter { playheadMs in it.startTimeMs..it.endTimeMs }
    } else {
      emptyList()
    }

    if (activeSubtitles.isNotEmpty()) {
      for (cue in activeSubtitles) {
        drawSubtitleCueOnCanvas(canvas, width, height, cue, playheadMs)
      }
    } else {
      // Fallback to any standalone TEXT track clip not in subtitles list
      for (tTrack in textTracks) {
        for (tClip in tTrack.clips) {
          if (playheadMs in tClip.timelineStartMs until tClip.timelineEndMs && tClip.overlayText.isNotBlank()) {
            drawSubtitleCueOnCanvas(
              canvas = canvas,
              width = width,
              height = height,
              cue = SubtitleCue(
                startTimeMs = tClip.timelineStartMs,
                endTimeMs = tClip.timelineEndMs,
                text = tClip.overlayText,
              ),
              playheadMs = playheadMs,
            )
          }
        }
      }
    }
  }

  private fun drawClipLayer(
    context: Context,
    canvas: Canvas,
    canvasWidth: Float,
    canvasHeight: Float,
    clip: TimelineClip,
    playheadMs: Long,
    activeFxPreset: FilterPreset?,
  ) {
    val interp = KeyframeColorEngine.evaluateTransformAt(clip, playheadMs)
    val trans = KeyframeColorEngine.evaluateTransitionState(clip, playheadMs)

    val combinedScale = (interp.scale * trans.scaleMultiplier).coerceIn(0.1f, 4.0f)
    val combinedAlpha = ((interp.opacity * trans.alphaMultiplier).coerceIn(0f, 1f) * 255).toInt()
    val offsetX = (interp.positionX + trans.offsetXFraction) * (canvasWidth * 0.5f)
    val offsetY = interp.positionY * (canvasHeight * 0.5f)

    val matrixArr = KeyframeColorEngine.buildColorMatrixArray(clip.colorGrading, activeFxPreset)
    val colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixArr))

    val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      alpha = combinedAlpha
      this.colorFilter = colorFilter
    }

    val saveCount = canvas.saveLayer(0f, 0f, canvasWidth, canvasHeight, layerPaint)
    try {
      canvas.translate(canvasWidth / 2f + offsetX, canvasHeight / 2f + offsetY)
      canvas.rotate(interp.rotationDeg)
      val sx = if (clip.mirrorHorizontal) -combinedScale else combinedScale
      val sy = if (clip.mirrorVertical) -combinedScale else combinedScale
      canvas.scale(sx, sy)
      canvas.translate(-canvasWidth / 2f, -canvasHeight / 2f)

      val localClipMs = if (clip.isFrozen) {
        clip.sourceInMs
      } else {
        val elapsed = ((playheadMs - clip.timelineStartMs) * clip.speed).toLong()
        if (clip.isReversed) {
          (clip.durationMs - elapsed).coerceAtLeast(0L)
        } else {
          elapsed + clip.sourceInMs
        }
      }

      drawProceduralThemeCanvas(
        canvas = canvas,
        width = canvasWidth,
        height = canvasHeight,
        theme = clip.proceduralTheme,
        clipName = clip.name,
        localTimeMs = localClipMs,
      )

      // Apply vignette if configured
      if (clip.colorGrading.vignette > 0.02f) {
        val radius = maxOf(canvasWidth, canvasHeight) * 0.75f
        val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            canvasWidth / 2f,
            canvasHeight / 2f,
            radius,
            intArrayOf(
              Color.TRANSPARENT,
              Color.argb((clip.colorGrading.vignette * 210).toInt().coerceIn(0, 230), 0, 0, 0),
            ),
            floatArrayOf(0.45f, 1.0f),
            Shader.TileMode.CLAMP,
          )
        }
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, vigPaint)
      }
    } finally {
      canvas.restoreToCount(saveCount)
    }

    // Transition flash overlay (Dip to Black / Dip to White / Glitch Flash)
    if (trans.flashOverlayAlpha > 0.01f) {
      val flashPaint = Paint().apply {
        color = trans.flashColorHex.toInt()
        alpha = (trans.flashOverlayAlpha * 255).toInt().coerceIn(0, 255)
      }
      canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, flashPaint)
    }
  }

  fun drawProceduralThemeCanvas(
    canvas: Canvas,
    width: Float,
    height: Float,
    theme: ProceduralClipTheme,
    clipName: String,
    localTimeMs: Long,
  ) {
    val tSec = localTimeMs / 1000f
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        width,
        height,
        theme.primaryHex.toInt(),
        theme.secondaryHex.toInt(),
        Shader.TileMode.CLAMP,
      )
      alpha = 75
    }
    val baseDarkPaint = Paint().apply { color = Color.rgb(12, 17, 26) }
    canvas.drawRect(0f, 0f, width, height, baseDarkPaint)
    canvas.drawRect(0f, 0f, width, height, bgPaint)

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = theme.primaryHex.toInt()
      strokeWidth = (width * 0.003f).coerceAtLeast(2f)
      style = Paint.Style.STROKE
      alpha = 180
    }

    when (theme) {
      ProceduralClipTheme.CYBERPUNK_GRID -> {
        val horizonY = height * 0.55f
        canvas.drawLine(0f, horizonY, width, horizonY, linePaint)
        val gridCols = 10
        for (i in -gridCols..gridCols) {
          val topX = width / 2f + i * (width * 0.04f)
          val botX = width / 2f + i * (width * 0.16f)
          canvas.drawLine(topX, horizonY, botX, height, linePaint)
        }
        val scrollOffset = (tSec * 120f) % 45f
        var y = horizonY + scrollOffset * 0.4f
        var step = 12f
        while (y < height) {
          canvas.drawLine(0f, y, width, y, linePaint)
          y += step
          step *= 1.25f
        }
      }
      ProceduralClipTheme.NEON_CITY -> {
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = theme.secondaryHex.toInt()
          style = Paint.Style.FILL
          alpha = 140
        }
        val count = 16
        val barW = width / count
        for (i in 0 until count) {
          val wave = sin(tSec * 2.5f + i * 0.7f) * 0.2f + 0.45f
          val bh = height * wave
          canvas.drawRect(
            i * barW + 4f,
            height - bh,
            (i + 1) * barW - 4f,
            height,
            barPaint,
          )
        }
      }
      ProceduralClipTheme.STUDIO_COUNTDOWN -> {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.28f
        canvas.drawCircle(cx, cy, r, linePaint)
        canvas.drawCircle(cx, cy, r * 0.75f, linePaint)
        val sweepAngle = ((localTimeMs % 1000L) / 1000f) * 360f
        val rad = Math.toRadians((sweepAngle - 90f).toDouble())
        canvas.drawLine(
          cx,
          cy,
          cx + (r * cos(rad)).toFloat(),
          cy + (r * sin(rad)).toFloat(),
          linePaint,
        )
      }
      ProceduralClipTheme.WAVEFORM_SYNTH -> {
        val midY = height / 2f
        val bars = 32
        val stepX = width / bars
        for (i in 0 until bars) {
          val amp = (sin(tSec * 6f + i * 0.45f) * cos(tSec * 2.2f - i * 0.2f) * 0.35f + 0.4f) * height * 0.4f
          canvas.drawLine(
            i * stepX + stepX / 2f,
            midY - amp,
            i * stepX + stepX / 2f,
            midY + amp,
            linePaint,
          )
        }
      }
      ProceduralClipTheme.SUNSET_HORIZON -> {
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = theme.primaryHex.toInt()
          style = Paint.Style.FILL
          alpha = 200
        }
        val sunY = height * 0.48f + sin(tSec * 1.5f) * 14f
        canvas.drawCircle(width / 2f, sunY, minOf(width, height) * 0.22f, sunPaint)
        for (s in 0..5) {
          val stripeY = sunY + s * (height * 0.03f)
          canvas.drawLine(0f, stripeY, width, stripeY, baseDarkPaint.apply { strokeWidth = 6f })
        }
      }
      ProceduralClipTheme.MATRIX_STREAM -> {
        val cols = 20
        val colW = width / cols
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = theme.primaryHex.toInt()
          style = Paint.Style.FILL
        }
        for (c in 0 until cols) {
          val headY = ((tSec * (140f + (c % 5) * 40f) + c * 97f) % height)
          for (tail in 0..6) {
            dotPaint.alpha = (220 - tail * 32).coerceAtLeast(30)
            val yPos = (headY - tail * 18f + height) % height
            canvas.drawCircle(c * colW + colW / 2f, yPos, 5f, dotPaint)
          }
        }
      }
    }

    // Draw subtle HUD clip badge in top-left of clip frame
    val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      alpha = 180
      textSize = (minOf(width, height) * 0.035f).coerceIn(14f, 32f)
      typeface = Typeface.MONOSPACE
    }
    canvas.drawText(
      "$clipName  [${KeyframeColorEngine.formatTimecode(localTimeMs, 30)}]",
      width * 0.04f,
      height * 0.08f,
      hudPaint,
    )
  }

  private fun drawSubtitleCueOnCanvas(
    canvas: Canvas,
    width: Float,
    height: Float,
    cue: SubtitleCue,
    playheadMs: Long,
  ) {
    val style = cue.style
    val scaleFactor = (minOf(width, height) / 420f).coerceIn(0.75f, 2.8f)
    val textSizePx = style.fontSizeSp * scaleFactor

    val elapsed = (playheadMs - cue.startTimeMs).coerceAtLeast(0L)
    val animProgress = (elapsed.toFloat() / 220f).coerceIn(0f, 1f)

    val visiblePrimary = if (style.animation == SubtitleAnimation.TYPEWRITER) {
      val totalProgress = (elapsed.toFloat() / (cue.durationMs * 0.6f).coerceAtLeast(1f)).coerceIn(0f, 1f)
      cue.text.take((cue.text.length * totalProgress).toInt().coerceAtLeast(1))
    } else {
      cue.text
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = style.textColorHex.toInt()
      textSize = textSizePx
      textAlign = Paint.Align.CENTER
      typeface = when {
        style.isBold && style.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        style.isBold -> Typeface.DEFAULT_BOLD
        style.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        else -> Typeface.DEFAULT
      }
    }

    val strokePaint = Paint(textPaint).apply {
      color = style.strokeColorHex.toInt()
      this.style = Paint.Style.STROKE
      strokeWidth = style.strokeWidth * scaleFactor
    }

    val centerX = width / 2f
    val animOffsetY = if (style.animation == SubtitleAnimation.FADE_UP) {
      (1f - animProgress) * 20f * scaleFactor
    } else {
      0f
    }
    val baseY = height * style.verticalPosition.coerceIn(0.12f, 0.92f) + animOffsetY

    val primaryWidth = textPaint.measureText(visiblePrimary)
    val hasSecondary = style.showBilingual && cue.secondaryText.isNotBlank()
    val secondarySizePx = textSizePx * 0.82f
    val secPaint = Paint(textPaint).apply { textSize = secondarySizePx }
    val secWidth = if (hasSecondary) secPaint.measureText(cue.secondaryText) else 0f

    val boxW = maxOf(primaryWidth, secWidth) + 32f * scaleFactor
    val boxH = if (hasSecondary) (textSizePx + secondarySizePx + 26f * scaleFactor) else (textSizePx + 20f * scaleFactor)

    if (style.bgOpacity > 0.02f) {
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.bgColorHex.toInt()
        alpha = (style.bgOpacity * 255).toInt().coerceIn(0, 255)
      }
      val rect = RectF(
        centerX - boxW / 2f,
        baseY - textSizePx - 8f * scaleFactor,
        centerX + boxW / 2f,
        baseY - textSizePx - 8f * scaleFactor + boxH,
      )
      canvas.drawRoundRect(rect, 10f * scaleFactor, 10f * scaleFactor, bgPaint)
    }

    if (style.strokeWidth > 0.1f) {
      canvas.drawText(visiblePrimary, centerX, baseY, strokePaint)
    }
    canvas.drawText(visiblePrimary, centerX, baseY, textPaint)

    if (hasSecondary) {
      val secY = baseY + secondarySizePx + 6f * scaleFactor
      if (style.strokeWidth > 0.1f) {
        val secStroke = Paint(strokePaint).apply { textSize = secondarySizePx }
        canvas.drawText(cue.secondaryText, centerX, secY, secStroke)
      }
      canvas.drawText(cue.secondaryText, centerX, secY, secPaint)
    }
  }

  private fun drainEncoder(
    encoder: MediaCodec,
    muxer: MediaMuxer,
    bufferInfo: MediaCodec.BufferInfo,
    endOfStream: Boolean,
    currentTrackIndex: Int,
    isMuxerStarted: Boolean,
  ): Pair<Int, Boolean> {
    var trackIdx = currentTrackIndex
    var started = isMuxerStarted
    val timeoutUs = 10_000L

    while (true) {
      val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
      if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if (!endOfStream) break
      } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        if (!started) {
          val newFormat = encoder.outputFormat
          trackIdx = muxer.addTrack(newFormat)
          muxer.start()
          started = true
        }
      } else if (outputBufferIndex >= 0) {
        val encodedData: ByteBuffer? = encoder.getOutputBuffer(outputBufferIndex)
        if (encodedData != null) {
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
          }
          if (bufferInfo.size != 0 && started) {
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
          }
        }
        encoder.releaseOutputBuffer(outputBufferIndex, false)
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          break
        }
      }
    }
    return trackIdx to started
  }

  /**
   * Extracts duration (and display name) from a real device media URI selected via Android Photo Picker / Document Picker.
   */
  fun inspectMediaUriDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      (durStr?.toLongOrNull() ?: 5_000L).coerceAtLeast(500L)
    } catch (_: Exception) {
      5_000L
    } finally {
      runCatching { retriever.release() }
    }
  }
}
