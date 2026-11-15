package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseConfigVerificationTest {

    @Test
    fun googleServicesResources_areBundledForSathiAiProjectAndFirebaseInitializes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("com.aistudio.sathiai.qrxmzk", BuildConfig.APPLICATION_ID)
        assertEquals(
            "1:1056719363278:android:e9e0cad0fce1860f08049f",
            resolveFirebaseResourceString(context, "google_app_id")
        )
        assertEquals(
            "sathi-ai-6db27",
            resolveFirebaseResourceString(context, "project_id")
        )
        assertEquals(
            "1056719363278-8v3fkmkrt9mhtdtl8bqpv7t3j246osab.apps.googleusercontent.com",
            context.getString(R.string.default_web_client_id)
        )
        assertEquals("(default)", context.getString(R.string.firestore_database_id))

        val ready = isFirebaseReady(context)
        assertTrue("Expected isFirebaseReady(context) to be true when google-services.json is bundled", ready)

        val apps = FirebaseApp.getApps(context)
        assertTrue(apps.isNotEmpty())
        assertEquals("sathi-ai-6db27", apps.first().options.projectId)
        assertEquals("1:1056719363278:android:e9e0cad0fce1860f08049f", apps.first().options.applicationId)
    }
}
