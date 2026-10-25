package com.example.core

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONObject

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
) {
    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }
    val providerArray = org.json.JSONArray()
    currentUser?.providerData?.forEach { userInfo ->
        providerArray.put(
            JSONObject().apply {
                put("providerId", userInfo.providerId)
                put("displayName", userInfo.displayName)
                put("email", userInfo.email)
                put("phoneNumber", userInfo.phoneNumber)
                put("photoUrl", userInfo.photoUrl?.toString())
            }
        )
    }

    val authInfo = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("isAnonymous", currentUser?.isAnonymous)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", providerArray)
    }

    val errInfo = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put("authInfo", authInfo)
    }.toString()

    Log.e("FirestoreError", errInfo, exception)
    throw RuntimeException(errInfo, exception)
}
