package com.example.freizeit.data.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.freizeit.BuildConfig
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.ui.MainActivity

/**
 * Builds/shows/cancels the single check-in-prompt notification. Only ever one instance is
 * outstanding at a time (issue #28: "only the closest one's notification is shown/active"), so
 * every call reuses [NOTIFICATION_ID] rather than one ID per favorite.
 */
object GeofenceNotifications {

    private const val CHANNEL_ID = "checkin_proximity"
    private const val NOTIFICATION_ID = 4200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_checkin_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_checkin_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, poi: Poi) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val checkInIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CHECK_IN,
            Intent(context, GeofenceBroadcastReceiver::class.java)
                .setAction(GeofenceBroadcastReceiver.ACTION_CHECK_IN)
                .putExtra(GeofenceBroadcastReceiver.EXTRA_PLACE_ID, poi.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DISMISS,
            Intent(context, GeofenceBroadcastReceiver::class.java)
                .setAction(GeofenceBroadcastReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_checkin)
            .setContentTitle(context.getString(R.string.notification_checkin_title))
            .setContentText(
                context.getString(
                    R.string.notification_checkin_body,
                    poi.name ?: context.getString(R.string.checkin_history_unnamed_place)
                )
            )
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notification_checkin_action_check_in), checkInIntent)
            .addAction(0, context.getString(R.string.notification_checkin_action_dismiss), dismissIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (BuildConfig.DEBUG) {
                    // Debug builds shorten the DWELL delay (issue #34) so testers don't mistake
                    // the fast-firing notification for a bug where dwell time is being ignored.
                    setSubText(
                        "Debug build: fired after " +
                            "${GeofenceSyncManager.DEBUG_LOITERING_DELAY_MILLIS / 1000}s dwell, not 15 min"
                    )
                }
            }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private const val REQUEST_CODE_CONTENT = 1
    private const val REQUEST_CODE_CHECK_IN = 2
    private const val REQUEST_CODE_DISMISS = 3
}
