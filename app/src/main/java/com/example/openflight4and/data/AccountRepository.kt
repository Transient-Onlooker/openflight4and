package com.example.openflight4and.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(
        val firebaseUid: String,
        val userCode: String
    ) : AccountState
}

sealed interface AccountActionResult {
    data class Success(val account: AccountState.SignedIn, val ticketCount: Int) : AccountActionResult
    data class Error(val message: String) : AccountActionResult
}

class AccountRepository(
    private val context: Context,
    private val reportDataError: (String, Throwable) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }

    val accountState: Flow<AccountState> = context.dataStore.data.map { preferences ->
        val uid = preferences[AppPreferenceKeys.KEY_ACCOUNT_FIREBASE_UID]
        val userCode = preferences[AppPreferenceKeys.KEY_ACCOUNT_USER_CODE]
        if (uid.isNullOrBlank() || userCode.isNullOrBlank()) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn(uid, userCode)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): AccountActionResult {
        if (!ensureFirebaseInitialized()) {
            return AccountActionResult.Error(missingFirebaseConfigMessage())
        }
        if (BuildConfig.GOOGLE_SIGN_IN_SERVER_CLIENT_ID.isBlank()) {
            return AccountActionResult.Error("Google 로그인 클라이언트 ID가 없습니다.")
        }

        val firebaseAuth = FirebaseAuth.getInstance()
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            BuildConfig.GOOGLE_SIGN_IN_SERVER_CLIENT_ID
        )
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val googleCredential = try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return AccountActionResult.Error("Google 로그인 응답을 처리할 수 없습니다.")
            }
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: Exception) {
            reportDataError("Failed to get Google credential", e)
            return AccountActionResult.Error("Google 로그인을 완료하지 못했습니다.")
        }

        val firebaseUser = try {
            val authCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            firebaseAuth.signInWithCredential(authCredential).await().user
        } catch (e: Exception) {
            reportDataError("Failed to sign in with Firebase", e)
            return AccountActionResult.Error("Firebase 로그인에 실패했습니다.")
        } ?: return AccountActionResult.Error("Firebase 사용자 정보를 가져오지 못했습니다.")

        val idToken = getIdTokenOrNull()
            ?: return AccountActionResult.Error("로그인 토큰을 가져오지 못했습니다.")

        val session = try {
            openSession(idToken)
        } catch (e: Exception) {
            reportDataError("Failed to open account session", e)
            return AccountActionResult.Error("계정 서버에 연결하지 못했습니다.")
        }

        if (!session.ok) {
            return AccountActionResult.Error(session.error ?: "계정 서버 응답이 올바르지 않습니다.")
        }

        if (session.firebaseUid.isNullOrBlank() || session.userCode.isNullOrBlank() || session.ticketCount == null) {
            return AccountActionResult.Error("계정 서버 응답이 올바르지 않습니다.")
        }

        val signedIn = AccountState.SignedIn(
            firebaseUid = session.firebaseUid.ifBlank { firebaseUser.uid },
            userCode = session.userCode
        )
        saveAccount(signedIn)
        return AccountActionResult.Success(signedIn, session.ticketCount)
    }

    suspend fun getIdTokenOrNull(forceRefresh: Boolean = false): String? {
        if (!ensureFirebaseInitialized()) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: Exception) {
            reportDataError("Failed to get Firebase ID token", e)
            null
        }
    }

    suspend fun signOut() {
        if (ensureFirebaseInitialized()) {
            FirebaseAuth.getInstance().signOut()
        }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        context.dataStore.edit { preferences ->
            preferences.remove(AppPreferenceKeys.KEY_ACCOUNT_FIREBASE_UID)
            preferences.remove(AppPreferenceKeys.KEY_ACCOUNT_USER_CODE)
        }
    }

    suspend fun saveAccount(account: AccountState.SignedIn) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_ACCOUNT_FIREBASE_UID] = account.firebaseUid
            preferences[AppPreferenceKeys.KEY_ACCOUNT_USER_CODE] = account.userCode
        }
    }

    private fun ensureFirebaseInitialized(): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        if (
            BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank()
        ) {
            return false
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
        return true
    }

    private fun missingFirebaseConfigMessage(): String {
        val missing = buildList {
            if (BuildConfig.FIREBASE_API_KEY.isBlank()) add("FIREBASE_API_KEY")
            if (BuildConfig.FIREBASE_APP_ID.isBlank()) add("FIREBASE_APP_ID")
            if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) add("FIREBASE_PROJECT_ID")
        }
        return "Firebase 설정이 없습니다: ${missing.joinToString()}"
    }

    private suspend fun openSession(idToken: String): AccountSessionResponse = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.REDEEM_API_BASE_URL}/auth/session").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(AccountSessionRequest))
            }
            val statusCode = connection.responseCode
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseBody = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = try {
                json.decodeFromString<AccountSessionResponse>(responseBody)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Account session API returned unparsable payload. HTTP $statusCode: ${responseBody.take(500)}",
                    e
                )
            }
            if (statusCode !in 200..299 && response.error.isNullOrBlank()) {
                response.copy(error = "HTTP $statusCode")
            } else {
                response
            }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data object AccountSessionRequest

    @Serializable
    private data class AccountSessionResponse(
        val ok: Boolean = false,
        val error: String? = null,
        @SerialName("firebaseUid") val firebaseUid: String? = null,
        @SerialName("userCode") val userCode: String? = null,
        @SerialName("ticketCount") val ticketCount: Int? = null
    )
}
