package com.example

import com.example.core.CapCutDraftEngine
import com.example.core.ClipKeyframe
import com.example.core.ColorGrading
import com.example.core.EasingType
import com.example.core.FilterPreset
import com.example.core.KeyframeColorEngine
import com.example.core.SrtSubtitleEngine
import com.example.core.TimelineClip
import com.example.core.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

  @Test
  fun `srt parser and exporter handle bilingual subtitles accurately`() {
    val sampleSrt = """
      1
      00:00:01,250 --> 00:00:03,750
      Welcome to ReaCut NLE
      مرحبا بك في محرر الفيديو

      2
      00:00:04,000 --> 00:00:06,500
      Multi-Track Timeline & LUTs
    """.trimIndent()

    val parsed = SrtSubtitleEngine.parseSrtOrVtt(sampleSrt)
    assertEquals(2, parsed.size)
    assertEquals(1250L, parsed[0].startTimeMs)
    assertEquals(3750L, parsed[0].endTimeMs)
    assertEquals("Welcome to ReaCut NLE", parsed[0].text)
    assertEquals("مرحبا بك في محرر الفيديو", parsed[0].secondaryText)

    val exportedSrt = SrtSubtitleEngine.exportToSrt(parsed)
    assertTrue(exportedSrt.contains("00:00:01,250 --> 00:00:03,750"))
    assertTrue(exportedSrt.contains("مرحبا بك في محرر الفيديو"))
  }

  @Test
  fun `capcut draft importer parses microsecond segments and detects encrypted drafts`() {
    val sampleJson = CapCutDraftEngine.generateSampleCapCutDesktopDraftJson()
    val project = CapCutDraftEngine.importCapCutDraftJson(sampleJson)
    val report = project.lastImportReport

    assertNotNull(report)
    assertFalse(report!!.isEncryptedError)
    assertEquals(3, report.importedVideoCount)
    assertEquals(1, report.importedAudioCount)
    assertEquals(3, report.importedSubtitleCount)
    assertEquals(1, report.importedEffectCount)
    assertEquals(9000L, project.totalDurationMs)

    // Verify encrypted draft detection
    val encryptedProject = CapCutDraftEngine.importCapCutDraftJson("ENCRYPTED_CIPHER_PAYLOAD_2024")
    assertTrue(encryptedProject.lastImportReport?.isEncryptedError == true)
  }

  @Test
  fun `keyframe interpolator and color matrix engine compute expected values`() {
    val clip = TimelineClip(
      name = "TestClip",
      trackType = TrackType.VIDEO,
      timelineStartMs = 1000L,
      durationMs = 2000L,
      keyframes = listOf(
        ClipKeyframe(offsetMs = 0L, scale = 1.0f, positionX = 0f, opacity = 1.0f, easing = EasingType.LINEAR),
        ClipKeyframe(offsetMs = 2000L, scale = 2.0f, positionX = 0.5f, opacity = 0.5f, easing = EasingType.LINEAR),
      ),
    )

    val mid = KeyframeColorEngine.evaluateTransformAt(clip, 2000L) // +1000ms into clip (50%)
    assertEquals(1.5f, mid.scale, 0.02f)
    assertEquals(0.25f, mid.positionX, 0.02f)
    assertEquals(0.75f, mid.opacity, 0.02f)

    val matrix = KeyframeColorEngine.buildColorMatrixArray(
      ColorGrading(contrast = 1.2f, saturation = 1.1f, filterPreset = FilterPreset.CYBERPUNK_CYAN)
    )
    assertEquals(20, matrix.size)
  }
}
