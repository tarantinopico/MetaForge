package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.metaforge.detector.FormatDetector
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.SanitizationProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MetaForge", appName)
    }

    @Test
    fun `format capabilities for JPEG allow full sanitization`() {
        val caps = FormatDetector.getCapabilities(MediaFormat.JPEG)
        assertTrue(caps.canRead)
        assertTrue(caps.canSanitize)
        assertTrue(caps.canEdit)
        assertTrue(caps.supportedProfiles.contains(SanitizationProfileType.REMOVE_GPS))
        assertTrue(caps.supportedProfiles.contains(SanitizationProfileType.REMOVE_PERSONAL))
    }

    @Test
    fun `unknown format capability disables editing and sanitization`() {
        val caps = FormatDetector.getCapabilities(MediaFormat.UNKNOWN)
        assertFalse(caps.canRead)
        assertFalse(caps.canEdit)
        assertFalse(caps.canSanitize)
        assertTrue(caps.supportedProfiles.isEmpty())
    }
}
