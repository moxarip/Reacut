package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.example.core.CapCutDraftEngine
import com.example.core.RealMp4ExportEngine
import com.example.core.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ReaCut", appName)
  }

  @Test
  fun `composite frame renderer draws timeline clips and subtitles onto bitmap canvas`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val project = CapCutDraftEngine.createDefaultShowcaseProject()
    val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    RealMp4ExportEngine.renderCompositeFrameToCanvas(
      context = context,
      canvas = canvas,
      width = 640f,
      height = 360f,
      project = project,
      playheadMs = 1500L,
      burnInSubtitles = true,
    )

    // Verify lossless project serialization round-trip
    val serialized = CapCutDraftEngine.serializeReaCutProject(project)
    val restored = CapCutDraftEngine.deserializeReaCutProject(serialized)
    assertEquals(project.config.name, restored.config.name)
    assertEquals(
      project.tracks.first { it.type == TrackType.VIDEO }.clips.size,
      restored.tracks.first { it.type == TrackType.VIDEO }.clips.size,
    )
    assertTrue(CapCutDraftEngine.exportToCapCutDraftJson(project).contains("canvas_config"))
  }
}
