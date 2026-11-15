package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

internal fun resolveFirebaseResourceString(context: Context, resName: String): String {
    val packagesToCheck = listOfNotNull(
        context.packageName.takeIf { it.isNotBlank() },
        BuildConfig.APPLICATION_ID.takeIf { it.isNotBlank() },
        "com.example"
    ).distinct()
    for (pkg in packagesToCheck) {
        val resId = context.resources.getIdentifier(resName, "string", pkg)
        if (resId != 0) {
            val value = runCatching { context.getString(resId).trim() }.getOrNull()
            if (!value.isNullOrEmpty()) return value
        }
    }
    return ""
}

fun isFirebaseReady(context: Context): Boolean {
    return try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            return true
        }
        if (FirebaseApp.initializeApp(appCtx) != null) {
            return true
        }
        // Fallback: explicitly construct FirebaseOptions from generated google-services.json string resources
        // in case FirebaseOptions.fromResource failed due to package-name resolution mismatch.
        val appId = resolveFirebaseResourceString(appCtx, "google_app_id")
        val apiKey = resolveFirebaseResourceString(appCtx, "google_api_key")
        if (appId.isBlank() || apiKey.isBlank()) {
            return false
        }
        val projectId = resolveFirebaseResourceString(appCtx, "project_id")
        val senderId = resolveFirebaseResourceString(appCtx, "gcm_defaultSenderId")
        val storageBucket = resolveFirebaseResourceString(appCtx, "google_storage_bucket")
        val optionsBuilder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
        if (projectId.isNotBlank()) optionsBuilder.setProjectId(projectId)
        if (senderId.isNotBlank()) optionsBuilder.setGcmSenderId(senderId)
        if (storageBucket.isNotBlank()) optionsBuilder.setStorageBucket(storageBucket)
        FirebaseApp.initializeApp(appCtx, optionsBuilder.build())
        FirebaseApp.getApps(appCtx).isNotEmpty()
    } catch (e: IllegalStateException) {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            true
        } else {
            Log.w(TAG, "Firebase initialization state error: ${e.message}")
            false
        }
    } catch (e: Exception) {
        Log.w(TAG, "Firebase is not configured in this build: ${e.message}")
        false
    }
}

fun firebaseUnavailableReason(context: Context): String {
    val appCtx = context.applicationContext ?: context
    val hasAppId = resolveFirebaseResourceString(appCtx, "google_app_id").isNotBlank()
    val hasApiKey = resolveFirebaseResourceString(appCtx, "google_api_key").isNotBlank()
    return if (!hasAppId || !hasApiKey) {
        "এই বিল্ডে google-services.json যুক্ত নেই অথবা Firebase কনফিগারেশন অসম্পূর্ণ। সঠিক google-services.json দিয়ে অ্যাপটি পুনরায় বিল্ড করুন।"
    } else {
        "অনলাইন AI সেবা এই বিল্ডে প্রস্তুত নয়। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!isFirebaseReady(context)) {
            Log.w(TAG, "Skipping App Check setup because FirebaseApp is not initialized.")
            return
        }

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.setTokenAutoRefreshEnabled(true)

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit ensures DebugAppCheckProviderFactory reads the token immediately.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
