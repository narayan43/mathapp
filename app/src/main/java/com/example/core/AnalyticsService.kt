package com.example.core

import android.util.Log

/**
 * Thin AnalyticsService for logging key training and daily workout events.
 */
object AnalyticsService {
    private const val TAG = "NumeraAnalytics"

    private val recentEvents = mutableListOf<Pair<String, Map<String, Any?>>>()

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        synchronized(recentEvents) {
            recentEvents.add(name to params)
            if (recentEvents.size > 50) {
                recentEvents.removeAt(0)
            }
        }
        runCatching {
            Log.d(TAG, "Event: $name | Params: $params")
        }
    }

    fun getRecentEvents(): List<Pair<String, Map<String, Any?>>> = synchronized(recentEvents) {
        recentEvents.toList()
    }
}
