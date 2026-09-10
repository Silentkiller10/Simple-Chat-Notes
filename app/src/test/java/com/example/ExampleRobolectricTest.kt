package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChecklistItem
import com.example.media.MediaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Saved", appName)
    }

    @Test
    fun `test checklist json serialization and deserialization`() {
        val original = listOf(
            ChecklistItem(id = "1", text = "Buy milk", isDone = false),
            ChecklistItem(id = "2", text = "Workout", isDone = true)
        )
        val json = ChecklistItem.toJsonArray(original)
        val parsed = ChecklistItem.fromJsonArray(json)

        assertEquals(2, parsed.size)
        assertEquals("Buy milk", parsed[0].text)
        assertEquals(false, parsed[0].isDone)
        assertEquals("Workout", parsed[1].text)
        assertEquals(true, parsed[1].isDone)
    }

    @Test
    fun `test media utils tag and link extraction`() {
        val text = "Check this #work #ideas note at https://kotlinlang.org"
        val tags = MediaUtils.extractHashtags(text)
        val urls = MediaUtils.extractUrls(text)

        assertTrue(tags.contains("#work"))
        assertTrue(tags.contains("#ideas"))
        assertEquals("https://kotlinlang.org", urls.firstOrNull())
        assertEquals("kotlinlang.org", MediaUtils.extractDomain("https://kotlinlang.org/docs"))
    }
}
