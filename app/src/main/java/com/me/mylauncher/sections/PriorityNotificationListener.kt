package com.me.mylauncher.sections

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PriorityNotification(
    val key: String,
    val appName: String,
    val title: String,
    val message: String,
    val time: String,
    val timestamp: Long,
    val contentIntent: PendingIntent?
)

object PriorityNotificationStore {

    private val _notifications =
        MutableStateFlow<List<PriorityNotification>>(emptyList())

    val notifications = _notifications.asStateFlow()

    internal fun update(notification: PriorityNotification) {
        _notifications.value =
            (_notifications.value.filter { it.key != notification.key } +
                    notification)
                .sortedByDescending { it.timestamp }
                .take(10)
    }

    internal fun remove(key: String) {
        _notifications.value =
            _notifications.value.filterNot { it.key == key }
    }

    internal fun replace(items: List<PriorityNotification>) {
        _notifications.value = items
            .sortedByDescending { it.timestamp }
            .take(10)
    }
}

class PriorityNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        val current = activeNotifications ?: return
        val rankingMap = currentRanking

        PriorityNotificationStore.replace(
            current.mapNotNull { sbn ->
                createPriorityNotification(
                    sbn,
                    rankingMap
                )
            }
        )
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {
        super.onNotificationPosted(sbn, rankingMap)

        val notification = createPriorityNotification(
            sbn,
            rankingMap
        )

        if (notification != null) {
            PriorityNotificationStore.update(notification)
        } else {
            PriorityNotificationStore.remove(sbn.key)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {
        super.onNotificationRemoved(sbn)
        PriorityNotificationStore.remove(sbn.key)
    }

    private fun createPriorityNotification(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ): PriorityNotification? {

        val notification = sbn.notification

        if (sbn.packageName == packageName) return null

        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return null
        }

        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            return null
        }

        val category = notification.category

        val importantCategory = category in setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_MESSAGE,
            Notification.CATEGORY_EMAIL,
            Notification.CATEGORY_EVENT,
            Notification.CATEGORY_ALARM
        )

        val ranking = Ranking()
        val hasRanking = rankingMap?.getRanking(sbn.key, ranking) == true
        val importance = if (hasRanking) {
            ranking.importance
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }

        if (
            importance < NotificationManager.IMPORTANCE_HIGH &&
            !importantCategory
        ) {
            return null
        }

        val extras = notification.extras

        val title = extras.getCharSequence(
            Notification.EXTRA_TITLE
        )?.toString()?.takeIf { it.isNotBlank() }
            ?: return null

        val message = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                )?.toString().orEmpty()

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(
                sbn.packageName,
                0
            )
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast(".")
        }

        val time = DateFormat.format(
            "h:mm a",
            sbn.postTime
        ).toString()

        return PriorityNotification(
            key = sbn.key,
            appName = appName,
            title = title,
            message = message,
            time = time,
            timestamp = sbn.postTime,
            contentIntent = notification.contentIntent
        )
    }
}

fun isNotificationAccessEnabled(context: Context): Boolean {
    return try {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        enabledListeners.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}