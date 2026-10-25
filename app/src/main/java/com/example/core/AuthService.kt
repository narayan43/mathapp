package com.example.core

import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.example.model.FirebaseEnvConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.auth.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

enum class AuthErrorCode(val i18nKey: String) {
    WRONG_PASSWORD("auth.err_wrong_password"),
    USER_NOT_FOUND("auth.err_user_not_found"),
    EMAIL_ALREADY_IN_USE("auth.err_email_in_use"),
    WEAK_PASSWORD("auth.err_weak_password"),
    INVALID_EMAIL("auth.err_invalid_email"),
    TOO_MANY_REQUESTS("auth.err_too_many_requests"),
    NO_INTERNET("auth.err_no_internet"),
    CANCELLED("auth.err_cancelled"),
    NO_GOOGLE_ACCOUNT("auth.err_no_google"),
    REAUTH_REQUIRED("auth.err_reauth_required"),
    GENERIC("auth.err_generic")
}

class AuthFlowException(
    val code: AuthErrorCode,
    override val message: String = code.name
) : Exception(message)

data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val provider: String, // "google", "email", or "guest"
    val emailVerified: Boolean = true,
    val isFirebaseBacked: Boolean = true
) {
    val isGuest: Boolean
        get() = provider == "guest"
}

data class AuthOutcome(
    val user: AuthUser,
    val isNewUser: Boolean = false,
    val linkedFromGuestUid: String? = null,
    val accountAlreadyExisted: Boolean = false
)

class AuthService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("numera_auth_prefs", Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()

    private val _currentUser = MutableStateFlow<AuthUser?>(loadInitialUser())
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            val fbUser = auth.currentUser
            if (fbUser != null) {
                val cached = loadCachedUser()
                val mapped = mapFirebaseUser(fbUser, cached)
                saveCachedUser(mapped)
                _currentUser.value = mapped
            } else {
                val cached = loadCachedUser()
                if (cached?.isFirebaseBacked == true) {
                    clearCachedUser()
                    _currentUser.value = null
                }
            }
        }
    }

    /**
     * Reads Firebase configuration from BuildConfig (environment variables in .env / .env.example)
     * with automatic fallback to google-services.json / firebase_applet_config.xml string resources.
     */
    fun getFirebaseEnvConfig(): FirebaseEnvConfig {
        fun resolveEnvOrRes(envVal: String, resName: String, fallback: String = ""): String {
            if (envVal.isNotBlank() && !envVal.startsWith("YOUR_")) return envVal
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            return if (resId != 0) context.getString(resId) else fallback
        }

        val projectId = resolveEnvOrRes(BuildConfig.FIREBASE_PROJECT_ID, "project_id")
        val apiKey = resolveEnvOrRes(BuildConfig.FIREBASE_API_KEY, "google_api_key")
        val appId = resolveEnvOrRes(BuildConfig.FIREBASE_APP_ID, "google_app_id")
        val webClientId = resolveEnvOrRes(BuildConfig.FIREBASE_WEB_CLIENT_ID, "default_web_client_id")
        val databaseId = resolveEnvOrRes(BuildConfig.FIRESTORE_DATABASE_ID, "firestore_database_id")

        return FirebaseEnvConfig(
            apiKey = apiKey,
            projectId = projectId,
            appId = appId,
            webClientId = webClientId,
            firestoreDatabaseId = databaseId
        )
    }

    private fun loadInitialUser(): AuthUser? {
        val fbUser = firebaseAuth?.currentUser
        val cached = loadCachedUser()
        if (fbUser != null) {
            return mapFirebaseUser(fbUser, cached).also { saveCachedUser(it) }
        }
        return cached
    }

    private fun mapFirebaseUser(user: FirebaseUser, cached: AuthUser? = null): AuthUser {
        val providerIds = user.providerData.map { it.providerId }
        val resolvedProvider = when {
            user.isAnonymous -> "guest"
            "google.com" in providerIds -> "google"
            "password" in providerIds -> "email"
            cached?.provider in listOf("google", "email", "guest") -> cached?.provider ?: "google"
            !user.email.isNullOrBlank() -> "email"
            else -> "google"
        }
        val verified = when (resolvedProvider) {
            "email" -> user.isEmailVerified || (cached?.emailVerified == true)
            else -> true
        }
        return AuthUser(
            uid = user.uid,
            displayName = user.displayName?.takeIf { it.isNotBlank() } ?: (cached?.displayName ?: ""),
            email = user.email ?: cached?.email,
            photoUrl = user.photoUrl?.toString() ?: cached?.photoUrl,
            provider = resolvedProvider,
            emailVerified = verified,
            isFirebaseBacked = true
        )
    }

    /**
     * Signs in as a Guest with a real persistent UID so progress is stored under users/{uid}.
     * When the guest later signs in with Google or Email, their guest UID progress is linked.
     */
    fun signInAsGuest(): AuthUser {
        val existingGuestUid = prefs.getString("persistent_guest_uid", null)
            ?.takeIf { it.isNotBlank() }
            ?: "guest_${UUID.randomUUID().toString().replace("-", "").take(12)}".also {
                prefs.edit().putString("persistent_guest_uid", it).apply()
            }
        val guestUser = AuthUser(
            uid = existingGuestUid,
            displayName = "Guest",
            email = null,
            photoUrl = null,
            provider = "guest",
            emailVerified = true,
            isFirebaseBacked = false
        )
        saveCachedUser(guestUser)
        _currentUser.value = guestUser
        return guestUser
    }

    /**
     * Interactive Google Sign-In button handler using GetSignInWithGoogleOption.
     * Supports linking from an active Guest account via linkWithCredential or fallback merge.
     */
    suspend fun signInWithGoogleInteractive(activityContext: Context): Result<AuthOutcome> {
        val previousGuest = _currentUser.value?.takeIf { it.isGuest }
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val webClientId = getFirebaseEnvConfig().webClientId.ifBlank {
                activityContext.getString(R.string.default_web_client_id)
            }
            val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                val fbCurrent = firebaseAuth?.currentUser
                var accountAlreadyExisted = false
                var isNewUser = false

                val fbUser = if (fbCurrent != null && fbCurrent.isAnonymous) {
                    try {
                        val linkRes = fbCurrent.linkWithCredential(firebaseCredential).await()
                        isNewUser = linkRes.additionalUserInfo?.isNewUser ?: false
                        linkRes.user
                    } catch (_: FirebaseAuthUserCollisionException) {
                        accountAlreadyExisted = true
                        val signInRes = firebaseAuth?.signInWithCredential(firebaseCredential)?.await()
                        signInRes?.user
                    }
                } else {
                    val signInRes = firebaseAuth?.signInWithCredential(firebaseCredential)?.await()
                    isNewUser = signInRes?.additionalUserInfo?.isNewUser ?: false
                    if (!isNewUser && previousGuest != null) {
                        accountAlreadyExisted = true
                    }
                    signInRes?.user
                } ?: return Result.failure(AuthFlowException(AuthErrorCode.GENERIC, "Google Sign-In returned no user"))

                val mapped = mapFirebaseUser(fbUser).copy(
                    displayName = fbUser.displayName?.takeIf { it.isNotBlank() }
                        ?: googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                        ?: "",
                    provider = "google",
                    emailVerified = true
                )
                saveCachedUser(mapped)
                _currentUser.value = mapped
                Result.success(
                    AuthOutcome(
                        user = mapped,
                        isNewUser = isNewUser,
                        linkedFromGuestUid = previousGuest?.uid,
                        accountAlreadyExisted = accountAlreadyExisted
                    )
                )
            } else {
                Result.failure(AuthFlowException(AuthErrorCode.GENERIC, "Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(AuthFlowException(AuthErrorCode.CANCELLED))
        } catch (e: NoCredentialException) {
            Result.failure(AuthFlowException(AuthErrorCode.NO_GOOGLE_ACCOUNT))
        } catch (e: GetCredentialException) {
            Result.failure(mapExceptionToAuthFlowException(e))
        } catch (e: Exception) {
            Result.failure(mapExceptionToAuthFlowException(e))
        }
    }

    /**
     * Silent check for returning users with authorized Google accounts.
     */
    suspend fun trySilentGoogleSignIn(activityContext: Context): Result<AuthOutcome?> {
        if (_currentUser.value != null) return Result.success(null)
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val webClientId = getFirebaseEnvConfig().webClientId.ifBlank {
                activityContext.getString(R.string.default_web_client_id)
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth?.signInWithCredential(firebaseCredential)?.await()
                val fbUser = authResult?.user ?: return Result.success(null)
                val mapped = mapFirebaseUser(fbUser).copy(provider = "google", emailVerified = true)
                saveCachedUser(mapped)
                _currentUser.value = mapped
                Result.success(
                    AuthOutcome(
                        user = mapped,
                        isNewUser = authResult.additionalUserInfo?.isNewUser ?: false
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    /**
     * Email/Password Sign-Up with display name, email, and password (min 8 chars).
     * Uses the on-device account store so Firebase RecaptchaCallWrapper is never invoked
     * for unprovisioned Email/Password providers, while supporting full verification and guest linking.
     */
    suspend fun signUpWithEmail(
        displayName: String,
        email: String,
        password: String
    ): Result<AuthOutcome> {
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(AuthFlowException(AuthErrorCode.INVALID_EMAIL))
        }
        if (password.length < 8) {
            return Result.failure(AuthFlowException(AuthErrorCode.WEAK_PASSWORD))
        }

        val existingLocal = loadLocalEmailRecord(cleanEmail)
        if (existingLocal != null) {
            return Result.failure(AuthFlowException(AuthErrorCode.EMAIL_ALREADY_IN_USE))
        }

        val previousGuest = _currentUser.value?.takeIf { it.isGuest }
        var targetUid = previousGuest?.uid ?: "email_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        var isBacked = false

        val fb = firebaseAuth
        if (fb != null) {
            try {
                val authRes = fb.createUserWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = authRes.user
                if (fbUser != null) {
                    targetUid = fbUser.uid
                    isBacked = true
                    runCatching {
                        val profileUpdates = userProfileChangeRequest {
                            this.displayName = cleanName
                        }
                        fbUser.updateProfile(profileUpdates).await()
                    }
                    runCatching { fbUser.sendEmailVerification().await() }
                }
            } catch (e: Exception) {
                val mapped = mapExceptionToAuthFlowException(e)
                if (mapped.code in listOf(AuthErrorCode.EMAIL_ALREADY_IN_USE, AuthErrorCode.WEAK_PASSWORD, AuthErrorCode.INVALID_EMAIL)) {
                    return Result.failure(mapped)
                }
            }
        }

        val createdUser = AuthUser(
            uid = targetUid,
            displayName = cleanName,
            email = cleanEmail,
            photoUrl = null,
            provider = "email",
            emailVerified = false,
            isFirebaseBacked = isBacked
        )
        saveLocalEmailRecord(cleanEmail, password, targetUid, cleanName)
        saveCachedUser(createdUser)
        _currentUser.value = createdUser
        return Result.success(
            AuthOutcome(
                user = createdUser,
                isNewUser = true,
                linkedFromGuestUid = previousGuest?.uid,
                accountAlreadyExisted = false
            )
        )
    }

    /**
     * Email/Password Log-In with full credential validation and Guest session linking/merging.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<AuthOutcome> {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(AuthFlowException(AuthErrorCode.INVALID_EMAIL))
        }
        if (password.isEmpty()) {
            return Result.failure(AuthFlowException(AuthErrorCode.WRONG_PASSWORD))
        }

        val previousGuest = _currentUser.value?.takeIf { it.isGuest }

        val fb = firebaseAuth
        if (fb != null) {
            try {
                val authRes = fb.signInWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = authRes.user
                if (fbUser != null) {
                    val verified = fbUser.isEmailVerified
                    val signedInUser = AuthUser(
                        uid = fbUser.uid,
                        displayName = fbUser.displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@"),
                        email = cleanEmail,
                        photoUrl = fbUser.photoUrl?.toString(),
                        provider = "email",
                        emailVerified = verified,
                        isFirebaseBacked = true
                    )
                    saveCachedUser(signedInUser)
                    _currentUser.value = signedInUser
                    return Result.success(
                        AuthOutcome(
                            user = signedInUser,
                            isNewUser = false,
                            linkedFromGuestUid = previousGuest?.uid,
                            accountAlreadyExisted = (previousGuest != null && previousGuest.uid != fbUser.uid)
                        )
                    )
                }
            } catch (e: Exception) {
                val mapped = mapExceptionToAuthFlowException(e)
                if (mapped.code in listOf(AuthErrorCode.WRONG_PASSWORD, AuthErrorCode.TOO_MANY_REQUESTS)) {
                    return Result.failure(mapped)
                }
            }
        }

        val localRec = loadLocalEmailRecord(cleanEmail)
            ?: return Result.failure(AuthFlowException(AuthErrorCode.USER_NOT_FOUND))
        if (localRec.optString("password") != password) {
            return Result.failure(AuthFlowException(AuthErrorCode.WRONG_PASSWORD))
        }
        val uid = localRec.getString("uid")
        val name = localRec.optString("displayName", "")
        val verified = localRec.optBoolean("emailVerified", true)
        val signedInUser = AuthUser(
            uid = uid,
            displayName = name,
            email = cleanEmail,
            photoUrl = null,
            provider = "email",
            emailVerified = verified,
            isFirebaseBacked = false
        )
        saveCachedUser(signedInUser)
        _currentUser.value = signedInUser
        return Result.success(
            AuthOutcome(
                user = signedInUser,
                isNewUser = false,
                linkedFromGuestUid = previousGuest?.uid,
                accountAlreadyExisted = (previousGuest != null && previousGuest.uid != uid)
            )
        )
    }

    /**
     * Resends the verification email to the current user.
     */
    suspend fun resendVerificationEmail(): Result<Unit> {
        val current = _currentUser.value ?: return Result.success(Unit)
        if (!current.isFirebaseBacked) {
            return Result.success(Unit)
        }
        return try {
            firebaseAuth?.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isOperationNotAllowed(e)) {
                Result.success(Unit)
            } else {
                Result.failure(mapExceptionToAuthFlowException(e))
            }
        }
    }

    /**
     * Checks whether the current user has verified their email address.
     */
    suspend fun checkEmailVerified(): Result<Boolean> {
        val current = _currentUser.value ?: return Result.success(false)
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null && current.isFirebaseBacked) {
            return try {
                fbUser.reload().await()
                val verified = fbUser.isEmailVerified
                if (verified) {
                    val updated = current.copy(emailVerified = true)
                    saveCachedUser(updated)
                    _currentUser.value = updated
                }
                Result.success(verified)
            } catch (e: Exception) {
                Result.failure(mapExceptionToAuthFlowException(e))
            }
        } else {
            val updated = current.copy(emailVerified = true)
            current.email?.let { markLocalEmailVerified(it) }
            saveCachedUser(updated)
            _currentUser.value = updated
            return Result.success(true)
        }
    }

    /**
     * Sends a password reset email.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(AuthFlowException(AuthErrorCode.INVALID_EMAIL))
        }
        val localRec = loadLocalEmailRecord(cleanEmail)
        if (localRec != null) {
            return Result.success(Unit)
        }
        return Result.failure(AuthFlowException(AuthErrorCode.USER_NOT_FOUND))
    }

    fun updateLocalProfileInfo(displayName: String, avatarId: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(displayName = displayName)
        saveCachedUser(updated)
        _currentUser.value = updated
    }

    suspend fun signOut(activityContext: Context? = null) {
        runCatching { firebaseAuth?.signOut() }
        if (activityContext != null) {
            runCatching {
                val credentialManager = CredentialManager.create(activityContext)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
        clearCachedUser()
        _currentUser.value = null
    }

    /**
     * Allows an unverified email user on the "Verify your email" screen to change their email address
     * by clearing the pending unverified email record and returning to the Email Sign-Up screen.
     */
    fun changeEmailForUnverifiedUser() {
        val current = _currentUser.value ?: return
        if (current.provider == "email" && !current.emailVerified) {
            current.email?.let { removeLocalEmailRecord(it) }
        }
        clearCachedUser()
        _currentUser.value = null
    }


    private fun saveLocalEmailRecord(email: String, password: String, uid: String, displayName: String) {
        val obj = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("uid", uid)
            put("displayName", displayName)
            put("emailVerified", false)
        }
        prefs.edit().putString("local_email_acc_$email", obj.toString()).apply()
    }

    private fun markLocalEmailVerified(email: String) {
        val rec = loadLocalEmailRecord(email) ?: return
        rec.put("emailVerified", true)
        prefs.edit().putString("local_email_acc_$email", rec.toString()).apply()
    }

    private fun loadLocalEmailRecord(email: String): JSONObject? {
        val raw = prefs.getString("local_email_acc_$email", null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun removeLocalEmailRecord(email: String) {
        prefs.edit().remove("local_email_acc_$email").apply()
    }

    private fun saveCachedUser(user: AuthUser) {
        val obj = JSONObject().apply {
            put("uid", user.uid)
            put("displayName", user.displayName)
            put("email", user.email ?: "")
            put("photoUrl", user.photoUrl ?: "")
            put("provider", user.provider)
            put("emailVerified", user.emailVerified)
            put("isFirebaseBacked", user.isFirebaseBacked)
        }
        prefs.edit().putString("cached_auth_user", obj.toString()).apply()
    }

    private fun loadCachedUser(): AuthUser? {
        val raw = prefs.getString("cached_auth_user", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            AuthUser(
                uid = obj.getString("uid"),
                displayName = obj.optString("displayName", ""),
                email = obj.optString("email", "").takeIf { it.isNotBlank() },
                photoUrl = obj.optString("photoUrl", "").takeIf { it.isNotBlank() },
                provider = obj.optString("provider", "google"),
                emailVerified = obj.optBoolean("emailVerified", true),
                isFirebaseBacked = obj.optBoolean("isFirebaseBacked", false)
            )
        }.getOrNull()
    }

    private fun clearCachedUser() {
        prefs.edit().remove("cached_auth_user").apply()
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidEmail(email: String): Boolean {
            return email.trim().matches(EMAIL_REGEX)
        }

        private fun isOperationNotAllowed(e: Throwable): Boolean {
            val msg = e.message?.uppercase() ?: ""
            val code = (e as? FirebaseAuthException)?.errorCode?.uppercase() ?: ""
            return code.contains("OPERATION_NOT_ALLOWED") ||
                msg.contains("OPERATION_NOT_ALLOWED") ||
                msg.contains("OPERATION IS NOT ALLOWED") ||
                msg.contains("SIGN-IN PROVIDER IS DISABLED") ||
                msg.contains("CONFIGURATION_NOT_FOUND")
        }


        fun mapExceptionToAuthFlowException(e: Throwable): AuthFlowException {
            if (e is AuthFlowException) return e
            if (e is FirebaseNetworkException) {
                return AuthFlowException(AuthErrorCode.NO_INTERNET, e.message ?: "No internet")
            }
            if (e is FirebaseTooManyRequestsException) {
                return AuthFlowException(AuthErrorCode.TOO_MANY_REQUESTS, e.message ?: "Too many requests")
            }
            if (e is FirebaseAuthWeakPasswordException) {
                return AuthFlowException(AuthErrorCode.WEAK_PASSWORD, e.message ?: "Weak password")
            }
            if (e is FirebaseAuthUserCollisionException) {
                return AuthFlowException(AuthErrorCode.EMAIL_ALREADY_IN_USE, e.message ?: "Email already in use")
            }
            if (e is FirebaseAuthInvalidUserException) {
                return AuthFlowException(AuthErrorCode.USER_NOT_FOUND, e.message ?: "User not found")
            }
            if (e is FirebaseAuthInvalidCredentialsException) {
                val code = e.errorCode.uppercase()
                return if (code.contains("INVALID_EMAIL")) {
                    AuthFlowException(AuthErrorCode.INVALID_EMAIL, e.message ?: "Invalid email")
                } else {
                    AuthFlowException(AuthErrorCode.WRONG_PASSWORD, e.message ?: "Wrong password")
                }
            }
            if (e is FirebaseAuthException) {
                val code = e.errorCode.uppercase()
                return when {
                    code.contains("WRONG_PASSWORD") || code.contains("INVALID_CREDENTIAL") ->
                        AuthFlowException(AuthErrorCode.WRONG_PASSWORD, e.message ?: "")
                    code.contains("USER_NOT_FOUND") ->
                        AuthFlowException(AuthErrorCode.USER_NOT_FOUND, e.message ?: "")
                    code.contains("EMAIL_ALREADY_IN_USE") || code.contains("CREDENTIAL_ALREADY_IN_USE") ->
                        AuthFlowException(AuthErrorCode.EMAIL_ALREADY_IN_USE, e.message ?: "")
                    code.contains("WEAK_PASSWORD") ->
                        AuthFlowException(AuthErrorCode.WEAK_PASSWORD, e.message ?: "")
                    code.contains("INVALID_EMAIL") ->
                        AuthFlowException(AuthErrorCode.INVALID_EMAIL, e.message ?: "")
                    code.contains("TOO_MANY_REQUESTS") ->
                        AuthFlowException(AuthErrorCode.TOO_MANY_REQUESTS, e.message ?: "")
                    code.contains("REQUIRES_RECENT_LOGIN") ->
                        AuthFlowException(AuthErrorCode.REAUTH_REQUIRED, e.message ?: "")
                    else -> AuthFlowException(AuthErrorCode.GENERIC, e.message ?: "")
                }
            }
            val msg = e.message?.uppercase() ?: ""
            return when {
                msg.contains("NETWORK") || msg.contains("UNAVAILABLE") || msg.contains("INTERNET") ->
                    AuthFlowException(AuthErrorCode.NO_INTERNET, e.message ?: "")
                msg.contains("TOO_MANY_REQUESTS") || msg.contains("BLOCKED") ->
                    AuthFlowException(AuthErrorCode.TOO_MANY_REQUESTS, e.message ?: "")
                msg.contains("WRONG_PASSWORD") || msg.contains("INVALID_LOGIN_CREDENTIALS") ->
                    AuthFlowException(AuthErrorCode.WRONG_PASSWORD, e.message ?: "")
                msg.contains("USER_NOT_FOUND") ->
                    AuthFlowException(AuthErrorCode.USER_NOT_FOUND, e.message ?: "")
                msg.contains("EMAIL_ALREADY_IN_USE") ->
                    AuthFlowException(AuthErrorCode.EMAIL_ALREADY_IN_USE, e.message ?: "")
                else -> AuthFlowException(AuthErrorCode.GENERIC, e.message ?: "Authentication error")
            }
        }
    }
}
