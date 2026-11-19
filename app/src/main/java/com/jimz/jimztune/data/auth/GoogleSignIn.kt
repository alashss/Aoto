package com.jimz.jimztune.data.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Login Google native lewat Credential Manager: sheet pilih akun muncul di dalam aplikasi. */
object GoogleSignIn {
    private tailrec fun Context.activityContext(): Context =
        if (this is android.app.Activity) this else (this as? ContextWrapper)?.baseContext?.activityContext() ?: this

    /** Mengembalikan ID token Google. [hashedNonce] = SHA-256 (hex) dari nonce mentah yang dikirim ke server. */
    suspend fun idToken(context: Context, hashedNonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(GoogleConfig.WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(context).getCredential(context.activityContext(), request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Jenis kredensial tidak dikenal")
    }
}
