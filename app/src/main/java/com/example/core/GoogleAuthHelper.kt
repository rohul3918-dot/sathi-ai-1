package com.example.core

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun safeCurrentUser(context: Context): FirebaseUser? {
    return try {
        if (!isFirebaseReady(context)) null else Firebase.auth.currentUser
    } catch (e: Exception) {
        Log.w("Auth", "Unable to read Firebase currentUser: ${e.message}")
        null
    }
}

fun safeAuthStateFlow(context: Context): Flow<FirebaseUser?> {
    if (!isFirebaseReady(context)) {
        return flowOf(null)
    }
    return try {
        val auth = Firebase.auth
        auth.authStateFlow()
    } catch (e: Exception) {
        Log.w("Auth", "Unable to observe Firebase auth state: ${e.message}")
        flowOf(null)
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private fun resolveWebClientId(context: Context): String {
    return try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Exception) {
        ""
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (!isFirebaseReady(context)) {
        onUnauthenticated()
        return
    }
    try {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val clientId = resolveWebClientId(context)
    if (clientId.isBlank()) {
        onUnauthenticated()
        return
    }

    val googleIdOption = try {
        GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
    } catch (e: Exception) {
        Log.w("Auth", "Failed to build GetGoogleIdOption: ${e.message}")
        onUnauthenticated()
        return
    }

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    if (!isFirebaseReady(context)) {
        onAuthError("Firebase কনফিগারেশন (google-services.json) এই বিল্ডে নেই। লোকাল মোড ব্যবহার করুন অথবা google-services.json যোগ করুন।")
        return
    }

    val clientId = resolveWebClientId(context)
    if (clientId.isBlank()) {
        onAuthError("Google Sign-In কনফিগার করা নেই (default_web_client_id অনুপস্থিত)।")
        return
    }

    val signInOption = try {
        GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    } catch (e: Exception) {
        onAuthError("Google Sign-In অপশন তৈরি করা যায়নি: ${e.localizedMessage ?: "Configuration error"}")
        return
    }
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activityContext = context as? Activity
                ?: throw IllegalStateException("Google Sign-In requires an Activity context.")
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutUser(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                try {
                    if (isFirebaseReady(context)) {
                        Firebase.auth.signOut()
                    }
                } catch (e: Exception) {
                    Log.w("Auth", "Firebase signOut skipped: ${e.message}")
                }
                onSignOutComplete()
            }
        }
    }
}
