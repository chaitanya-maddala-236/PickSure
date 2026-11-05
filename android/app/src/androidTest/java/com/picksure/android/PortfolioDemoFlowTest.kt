package com.picksure.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioDemoFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pickerCanReviewBatchAndFreshnessMonitor() {
        saveScreen("01-login")

        compose.onNodeWithText("Sign in").performClick()
        waitForText("Start batch", 20_000)
        saveScreen("02-dashboard")

        compose.onNodeWithText("Start batch").performClick()
        waitForText("Follow the shelf route")
        saveScreen("03-batch")

        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Scan product + lot. Send the freshest eligible stock.").performClick()
        waitForText("Lot traceability")
        compose.onNodeWithText("AI Freshness Advisor").performScrollTo().assertIsDisplayed()
        saveScreen("04-freshness-advisor")
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) {
            runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        }
    }

    private fun saveScreen(name: String) {
        compose.waitForIdle()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.getExternalFilesDir(null), "picksure-demo").apply { mkdirs() }
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(directory, "$name.png")).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }
}
