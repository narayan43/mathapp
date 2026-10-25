package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.I18nService
import com.example.core.StorageService
import com.example.model.AppThemeId
import com.example.model.PresetAvatar
import com.example.model.UserProfileState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app_name matches Numera and all 17 language files load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Numera", appName)

        val i18n = I18nService(context)
        assertEquals(17, I18nService.SUPPORTED_LANGUAGES.size)
        for (lang in I18nService.SUPPORTED_LANGUAGES) {
            i18n.setLanguage(lang.code)
            val homeLabel = i18n.t("nav.home")
            assertTrue("Language ${lang.code} returned empty nav.home", homeLabel.isNotBlank())
        }
    }

    @Test
    fun `dev-time check ensures every translation key used in source code exists in en json and all 17 language files and t never returns raw key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val i18n = I18nService(context)

        // Load raw en.json from assets
        val enJsonText = context.assets.open("i18n/en.json").bufferedReader().use { it.readText() }
        val enObj = JSONObject(enJsonText)
        val enKeys = mutableSetOf<String>()
        val iter = enObj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val v = enObj.optString(k, "")
            if (v.isNotBlank()) {
                enKeys.add(k)
            }
        }

        // Scan all Kotlin source files for translation keys
        val srcCandidates = listOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull { it.exists() }
        assertTrue("Could not locate src/main/java directory for dev-time key check", srcCandidates != null)

        val keyPattern = Regex("""(?:\.t|t)\(\s*"([a-z]+\.[a-z0-9_]+)"|(?:titleKey|subtitleKey|labelKey|i18nKey)\s*=\s*"([a-z]+\.[a-z0-9_]+)"""")
        val usedKeys = sortedSetOf<String>()
        srcCandidates!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                keyPattern.findAll(content).forEach { match ->
                    val k = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    if (k.isNotEmpty() && k != "google.com") {
                        usedKeys.add(k)
                    }
                }
            }

        assertTrue("Expected to discover > 150 translation keys in Kotlin source", usedKeys.size > 150)

        // 1. Verify every used key is in en.json
        val missingInEn = usedKeys.filter { it !in enKeys }
        assertTrue(
            "Missing translation keys in en.json: $missingInEn",
            missingInEn.isEmpty()
        )

        // 2. Explicitly verify auth.email_section_title and new option-based flow keys exist across all 17 languages
        val criticalAuthKeys = listOf(
            "auth.email_section_title",
            "auth.welcome_tagline",
            "auth.google_btn",
            "auth.continue_email",
            "auth.continue_guest",
            "auth.guest_device_only_caption",
            "auth.guest_sheet_title",
            "auth.guest_sheet_body",
            "auth.sign_in_instead",
            "auth.email_login_title",
            "auth.email_signup_title",
            "auth.full_name_label",
            "auth.confirm_password_label",
            "auth.switch_to_signup",
            "auth.switch_to_login",
            "auth.err_password_mismatch"
        )

        for (lang in I18nService.SUPPORTED_LANGUAGES) {
            val langJsonText = context.assets.open("i18n/${lang.code}.json").bufferedReader().use { it.readText() }
            val langObj = JSONObject(langJsonText)
            for (k in usedKeys + criticalAuthKeys) {
                val rawVal = langObj.optString(k, "")
                assertTrue(
                    "Key '$k' is missing or blank in ${lang.code}.json",
                    rawVal.isNotBlank()
                )
            }

            i18n.setLanguage(lang.code)
            for (k in usedKeys + criticalAuthKeys) {
                val translated = i18n.t(k)
                assertFalse(
                    "I18nService.t('$k') in ${lang.code} returned raw key '$translated'",
                    translated == k
                )
            }
        }

        // 3. Verify fallback for a completely unknown key never returns a raw dotted key
        val fallbackResult = i18n.t("auth.unmapped_future_key")
        assertFalse("Fallback should never return raw dotted key", fallbackResult.contains('.'))
        assertEquals("Unmapped future key", fallbackResult)
    }

    @Test
    fun `storage service persists user-scoped settings, profile, and mistakes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = StorageService(context)

        val initial = storage.loadSettings("guest")
        val modified = initial.copy(
            autoConfirm = false,
            languageCode = "hi",
            themeId = AppThemeId.DARK_PURPLE
        )
        storage.saveSettings(modified, "guest")

        val reloaded = storage.loadSettings("guest")
        assertEquals(false, reloaded.autoConfirm)
        assertEquals("hi", reloaded.languageCode)
        assertEquals(AppThemeId.DARK_PURPLE, reloaded.themeId)

        val profile = UserProfileState(
            userId = "uid_test_1",
            isGuest = false,
            displayName = "Arya",
            avatarId = PresetAvatar.OWL.id,
            provider = "google",
            emailVerified = true
        )
        storage.saveUserProfile(profile, "uid_test_1")
        val loadedProfile = storage.loadUserProfile("uid_test_1")
        assertEquals("uid_test_1", loadedProfile.userId)
        assertEquals("Arya", loadedProfile.displayName)
        assertEquals("owl", loadedProfile.avatarId)
        assertEquals("google", loadedProfile.provider)

        // Verify Guest UID -> Account UID copy preserves settings
        storage.saveSettings(modified, "guest_temp_uid")
        storage.copyUserLocalData("guest_temp_uid", "linked_account_uid")
        val linkedSettings = storage.loadSettings("linked_account_uid")
        assertEquals("hi", linkedSettings.languageCode)
        assertEquals(AppThemeId.DARK_PURPLE, linkedSettings.themeId)
    }

    @Test
    fun `AnswerLogService records logs to SQLite with dirty tracking, updates Mistake Book, and exports JSON and CSV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logService = com.example.core.AnswerLogService(context)
        val uid = "test_sql_user_${System.currentTimeMillis()}"

        val prob = com.example.model.Problem("p1", com.example.model.MathOp.MUL, 47, 8, 376L, "47 × 8")
        val log1 = com.example.core.AnswerLog(
            id = "log_1_${System.currentTimeMillis()}",
            ts = 1_760_000_000_000L,
            day = "2026-10-23",
            mode = "mul",
            op = "mul",
            category = "mul_2dx1d",
            difficulty = "medium",
            level = null,
            operands = listOf(47, 8),
            prompt = "47 × 8",
            expected = 376L,
            given = 366L,
            correct = false,
            timeMs = 4500L,
            attempts = 1,
            source = "normal",
            sessionId = "sess_1"
        )
        logService.record(
            userId = uid,
            log = log1,
            problemForMistakeBook = prob,
            userMedianMs = 3500L,
            recordingEnabled = true
        )

        val loadedLogs = logService.loadAnswerLogs(uid)
        assertEquals(1, loadedLogs.size)
        assertEquals("mul_2dx1d", loadedLogs.first().category)
        assertEquals(false, loadedLogs.first().correct)
        assertEquals(setOf("2026-10-23"), logService.getDistinctDaysForUser(uid))

        val mistakes = logService.loadMistakeBook(uid, includeMastered = false)
        assertEquals(1, mistakes.size)
        assertEquals("47 × 8", mistakes.first().prompt)

        val jsonExport = logService.exportDataAsJson(uid)
        assertTrue(jsonExport.contains("mul_2dx1d"))

        val csvExport = logService.exportDataAsCsv(uid)
        assertTrue(csvExport.contains("id,ts,day,mode,op,category"))
        assertTrue(csvExport.contains("mul_2dx1d"))

        val dayLogs = logService.loadAnswerLogsForDay(uid, "2026-10-23")
        assertEquals(1, dayLogs.size)
    }

    @Test
    fun `AuthService email sign-up, verification, login, and password reset succeed without RecaptchaCallWrapper errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authService = com.example.core.AuthService(context)

        kotlinx.coroutines.runBlocking {
            // 1. Sign up with email
            val signUpRes = authService.signUpWithEmail("Narayan", "narayan@numera.test", "Numera#2026")
            assertTrue(signUpRes.isSuccess)
            val outcome = signUpRes.getOrThrow()
            assertEquals("Narayan", outcome.user.displayName)
            assertEquals("narayan@numera.test", outcome.user.email)
            assertEquals("email", outcome.user.provider)
            assertFalse(outcome.user.emailVerified)

            // 2. Duplicate sign-up returns EMAIL_ALREADY_IN_USE
            val dupRes = authService.signUpWithEmail("Narayan", "narayan@numera.test", "Numera#2026")
            assertTrue(dupRes.isFailure)
            val dupErr = dupRes.exceptionOrNull() as? com.example.core.AuthFlowException
            assertEquals(com.example.core.AuthErrorCode.EMAIL_ALREADY_IN_USE, dupErr?.code)

            // 3. Confirm email verification
            val verifyRes = authService.checkEmailVerified()
            assertTrue(verifyRes.getOrDefault(false))
            assertTrue(authService.currentUser.value?.emailVerified == true)

            // 4. Sign out and sign back in with email & password
            authService.signOut()
            val wrongPwRes = authService.signInWithEmail("narayan@numera.test", "WrongPass123")
            assertTrue(wrongPwRes.isFailure)
            assertEquals(
                com.example.core.AuthErrorCode.WRONG_PASSWORD,
                (wrongPwRes.exceptionOrNull() as? com.example.core.AuthFlowException)?.code
            )

            val loginRes = authService.signInWithEmail("narayan@numera.test", "Numera#2026")
            assertTrue(loginRes.isSuccess)
            assertEquals("Narayan", loginRes.getOrThrow().user.displayName)
            assertTrue(loginRes.getOrThrow().user.emailVerified)

            // 5. Password reset succeeds for registered email
            val resetRes = authService.sendPasswordReset("narayan@numera.test")
            assertTrue(resetRes.isSuccess)
        }
    }
}

