package com.example.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.time.LocalDate
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val storage = StorageService(context)
        val settings = storage.loadSettings()
        val todayStr = LocalDate.now().toString()
        val daily = storage.loadDailyState(todayStr, settings)

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NotificationScheduler.rescheduleNotifications(
                context = context,
                settings = settings,
                isDailyDoneToday = daily?.isCompletedToday == true,
                currentStreak = daily?.streak?.current ?: 0
            )
            return
        }

        val i18n = I18nService(context).also { it.setLanguage(settings.languageCode) }
        val streak = daily?.streak?.current ?: 0
        val answerLogService = AnswerLogService(context)
        val userId = storage.loadUserProfile().userId

        when (intent?.action) {
            NotificationScheduler.ACTION_WEEKLY_SUMMARY -> {
                if (!settings.weeklySummaryReminderEnabled) return
                val logs = answerLogService.loadAnswerLogs(userId, limit = 5000)
                val aggs = answerLogService.loadDailyAggregates(userId)
                val snap = StatsService.buildDashboardSnapshot(
                    rawLogs = logs,
                    rolledAggregates = aggs,
                    dateRange = InsightsDateRange.DAYS_7,
                    todayStr = todayStr
                )
                if (snap.totalQuestionsThisWeek == 0) return
                val weakestCat = snap.categoryStats.maxByOrNull { it.weaknessScore }?.let {
                    i18n.t(CategoryRegistry.getCategoryInfo(it.categoryId).labelKey)
                } ?: "—"
                val accPct = (snap.overallAccuracy * 100).toInt()
                val speedPart = snap.speedChangePctVsLastWeek?.takeIf { it > 0 }?.let { "$it% faster, " } ?: ""
                val msg = i18n.t(
                    "notif.weekly_summary",
                    mapOf(
                        "count" to snap.totalQuestionsThisWeek.toString(),
                        "acc" to accPct.toString(),
                        "speed" to speedPart,
                        "weakest" to weakestCat
                    )
                )
                NotificationScheduler.showNotification(context, 2003, "Numera · Weekly Insights", msg)
                return
            }
            NotificationScheduler.ACTION_MISTAKE_REVIEW -> {
                if (!settings.mistakeReminderEnabled) return
                val dueCount = answerLogService.loadMistakeBook(userId, includeMastered = false)
                    .count { it.nextReviewDay <= todayStr }
                if (dueCount <= 0) return
                val msg = i18n.t("notif.mistakes_ready", mapOf("count" to dueCount.toString()))
                NotificationScheduler.showNotification(context, 2004, "Numera · Smart Practice", msg)
                return
            }
        }

        // Only notify for daily workout / streak if today's set isn't done
        if (daily?.isCompletedToday == true) return

        val (title, body, notifId) = when (intent?.action) {
            NotificationScheduler.ACTION_STREAK_WARNING -> {
                if (!settings.streakWarningEnabled || streak < 3) return
                Triple(
                    "Numera 🔥",
                    i18n.t("notif.streak_warning", mapOf("count" to streak.toString())),
                    2002
                )
            }
            else -> {
                if (!settings.reminderEnabled) return
                val subtitle = if (streak > 0) {
                    i18n.t("notif.daily_ready_streak", mapOf("count" to streak.toString()))
                } else {
                    i18n.t("notif.daily_brain")
                }
                Triple(
                    i18n.t("notif.daily_brain"),
                    subtitle,
                    2001
                )
            }
        }

        NotificationScheduler.showNotification(context, notifId, title, body)
    }
}

object NotificationScheduler {
    const val CHANNEL_ID = "numera_daily_channel"
    const val ACTION_DAILY_REMINDER = "com.aistudio.numera.DAILY_REMINDER"
    const val ACTION_STREAK_WARNING = "com.aistudio.numera.STREAK_WARNING"
    const val ACTION_WEEKLY_SUMMARY = "com.aistudio.numera.WEEKLY_SUMMARY"
    const val ACTION_MISTAKE_REVIEW = "com.aistudio.numera.MISTAKE_REVIEW"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun rescheduleNotifications(
        context: Context,
        settings: com.example.model.SettingsState,
        isDailyDoneToday: Boolean,
        currentStreak: Int,
        dueMistakesCount: Int = 0
    ) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val dailyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val dailyPending = PendingIntent.getBroadcast(
            context,
            2001,
            dailyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val streakIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_STREAK_WARNING
        }
        val streakPending = PendingIntent.getBroadcast(
            context,
            2002,
            streakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val weeklyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_WEEKLY_SUMMARY
        }
        val weeklyPending = PendingIntent.getBroadcast(
            context,
            2003,
            weeklyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mistakeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MISTAKE_REVIEW
        }
        val mistakePending = PendingIntent.getBroadcast(
            context,
            2004,
            mistakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.cancel(dailyPending)
        am.cancel(streakPending)
        am.cancel(weeklyPending)
        am.cancel(mistakePending)

        if (settings.reminderEnabled) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, settings.reminderHour)
                set(Calendar.MINUTE, settings.reminderMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (isDailyDoneToday || timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, dailyPending)
            }
        }

        if (settings.streakWarningEnabled && currentStreak >= 3 && !isDailyDoneToday) {
            val warnCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (warnCal.timeInMillis > System.currentTimeMillis()) {
                runCatching {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warnCal.timeInMillis, streakPending)
                }
            }
        }

        // Weekly summary notification (Sunday 18:00)
        if (settings.weeklySummaryReminderEnabled) {
            val sunCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sunCal.timeInMillis, weeklyPending)
            }
        }

        // "You have N mistakes ready to review" reminder (max once per day at 17:30)
        if (settings.mistakeReminderEnabled && dueMistakesCount > 0) {
            val mCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 17)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mCal.timeInMillis, mistakePending)
            }
        }
    }

    fun showNotification(context: Context, id: Int, title: String, message: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .build()
        runCatching {
            nm.notify(id, notif)
        }
    }
}
