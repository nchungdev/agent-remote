package app.duinch.agentremote.core.presentation.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.duinch.agentremote.MainActivity

actual object PlatformAlertNotifier {

    private const val CHANNEL_CONNECTION = "agent_remote_connection_alerts"
    private const val CHANNEL_APPROVAL = "agent_remote_approval_alerts"
    private const val NOTIF_ID_CONNECTION = 4101
    private const val NOTIF_ID_APPROVAL = 4102

    var isAppInForeground: Boolean = true

    actual fun notifyConnectionChange(title: String, message: String) {
        if (isAppInForeground) return
        val ctx = AppContextProvider.context ?: return
        postSystemNotification(
            context = ctx,
            channelId = CHANNEL_CONNECTION,
            channelName = "Trạng thái kết nối Agent",
            notificationId = NOTIF_ID_CONNECTION,
            title = title,
            message = message
        )
    }

    actual fun notifyAgentApproval(toolName: String, description: String) {
        if (isAppInForeground) return
        val ctx = AppContextProvider.context ?: return
        postSystemNotification(
            context = ctx,
            channelId = CHANNEL_APPROVAL,
            channelName = "Phê duyệt quyền thực thi Agent",
            notificationId = NOTIF_ID_APPROVAL,
            title = "Yêu cầu phê duyệt: $toolName",
            message = description.ifBlank { "Nhấn để mở ứng dụng và phê duyệt lệnh thực thi." }
        )
    }

    private fun postSystemNotification(
        context: Context,
        channelId: String,
        channelName: String,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            nm.notify(notificationId, notification)
        }
    }
}
