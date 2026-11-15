package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.configureAppCheck
import com.example.core.isFirebaseReady
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sathi AI", appName)
  }

  @Test
  fun `firebase resources and initialization succeed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val options = FirebaseOptions.fromResource(context)
    assertNotNull(options)
    assertEquals("sathi-ai-6db27", options?.projectId)
    assertTrue(isFirebaseReady(context))
    configureAppCheck(context, null)
  }
}
