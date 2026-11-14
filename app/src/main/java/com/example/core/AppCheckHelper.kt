package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"

fun isFirebaseReady(context: Context): Boolean {
    return try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            true
        } else {
            FirebaseApp.initializeApp(appCtx) != null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Firebase is not configured in this build: ${e.message}")
        false
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    // App Check disabled to prevent invalid token errors during development
    Log.d(TAG, "App Check skipped.")
}
