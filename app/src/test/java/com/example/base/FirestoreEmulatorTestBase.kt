package com.example.base

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
abstract class FirestoreEmulatorTestBase {
    lateinit var db: FirebaseFirestore
    lateinit var auth: FirebaseAuth
    lateinit var projectId: String
    lateinit var databaseId: String

    private var isEmulatorRunning = false

    @Before
    fun setupEmulators() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        projectId = FirebaseApp.getInstance().options.projectId
            ?: throw IllegalStateException("Project ID missing from google-services.json")

        val dbIdRes = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        databaseId = if (dbIdRes != 0) context.getString(dbIdRes) else "(default)"

        db = FirebaseFirestore.getInstance(databaseId)
        try {
            db.useEmulator("127.0.0.1", 8085)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        } catch (_: IllegalStateException) {
            // Already configured
        }

        auth = FirebaseAuth.getInstance()
        try {
            auth.useEmulator("127.0.0.1", 9099)
        } catch (_: IllegalStateException) {
            // Already configured
        }

        val emulatorAvailable = try {
            val s = java.net.Socket()
            s.connect(java.net.InetSocketAddress("127.0.0.1", 8085), 300)
            s.close()
            true
        } catch (_: Exception) {
            false
        }
        isEmulatorRunning = emulatorAvailable
        org.junit.Assume.assumeTrue("Firebase emulator not reachable on 127.0.0.1:8085", emulatorAvailable)

        loadSecurityRulesIntoEmulator()
    }

    private fun loadSecurityRulesIntoEmulator() {
        if (!isEmulatorRunning) return
        val rulesFile = listOf(
            java.io.File("firestore.rules"),
            java.io.File("../firestore.rules")
        ).firstOrNull { it.exists() } ?: return
        val rulesText = rulesFile.readText()
        val payload = org.json.JSONObject().apply {
            put("ignore_errors", true)
            put("rules", org.json.JSONObject().apply {
                put("files", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("name", "firestore.rules")
                        put("content", rulesText)
                    })
                })
            })
        }.toString()

        val paths = listOf(
            "/emulator/v1/projects/$projectId:securityRules",
            "/emulator/v1/projects/$projectId/databases/$databaseId:securityRules"
        )
        for (path in paths) {
            try {
                val url = URL("http://127.0.0.1:8085$path")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 2000
                    readTimeout = 5000
                }
                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    @After
    fun tearDownEmulators() {
        if (!isEmulatorRunning) return
        runCatching { auth.signOut() }
        runCatching { clearFirestoreEmulatorData() }
        runCatching { FirebaseFirestore.getInstance(databaseId).terminate() }
        runCatching { FirebaseApp.clearInstancesForTest() }
        runCatching { Shadows.shadowOf(Looper.getMainLooper()).idle() }
    }

    private fun clearFirestoreEmulatorData() {
        if (!isEmulatorRunning) return
        val url = URL("http://127.0.0.1:8085/emulator/v1/projects/$projectId/databases/$databaseId/documents")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 2000
            readTimeout = 5000
        }
        try {
            conn.responseCode
        } catch (_: Exception) {
            // ignore
        } finally {
            conn.disconnect()
        }
    }

    protected fun runEmulatorTest(block: suspend CoroutineScope.() -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        val deferred = scope.async { block() }
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 30_000L
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            mainLooper.idle()
            Thread.sleep(10)
        }
        if (!deferred.isCompleted) {
            deferred.cancel()
            throw AssertionError("Emulator test timed out after 30s")
        }
        runBlocking {
            deferred.await()
        }
    }

    protected suspend fun signInTestUser(email: String, password: String = "password123"): String {
        val result = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
        return result.user!!.uid
    }
}
