package com.example.freizeit.data.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.ui.MainActivity
import com.example.freizeit.util.millisUntilMidnight
import java.util.Date

/**
 * Builds/shows/cancels the check-in notifications (#81): one prompt per place, tagged with the
 * place id, so an unanswered prompt survives a later one for another place. A prompt has Check in
 * and Dismiss actions; tapping it opens the place on the Map and leaves it up. Check in replaces
 * it with a short-lived confirmation offering Undo.
 *
 * Every PendingIntent carries the place id in its data URI as well as its extras: PendingIntents
 * are told apart by intent data, not extras, so without it every place's actions would collapse
 * into the last one posted.
 */
object GeofenceNotifications {

    const val CHANNEL_ID = "checkin_proximity"
    private const val PROMPT_ID = 4200
    private const val CONFIRMATION_ID = 4201
    private const val CONFIRMATION_TIMEOUT_MILLIS = 5 * 60 * 1000L

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

    /**
     * Posts the prompt for [poi] at [postedAt]. Its text shows [arrivedAt], when the device
     * entered the geofence, and a Check in from it logs that time however late it's pressed
     * (#82). False when notifications are off and nothing was shown.
     */
    fun showPrompt(context: Context, poi: Poi, postedAt: Long, arrivedAt: Long): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val checkIn = receiverIntent(context, GeofenceBroadcastReceiver.ACTION_CHECK_IN, poi.id)
            .putExtra(GeofenceBroadcastReceiver.EXTRA_ARRIVED_AT, arrivedAt)
        val dismiss = receiverIntent(context, GeofenceBroadcastReceiver.ACTION_DISMISS, poi.id)
        val openOnMap = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_ON_MAP,
            Intent(context, MainActivity::class.java)
                .setData(placeUri(poi.id))
                .putExtra(MainActivity.EXTRA_OPEN_ON_MAP_POI_ID, poi.id)
                // A tap while the app is open reaches the running instance via onNewIntent.
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_checkin)
            .setContentTitle(placeName(context, poi.name))
            .setContentText(context.getString(R.string.notification_checkin_body, formatTime(context, arrivedAt)))
            .setWhen(postedAt)
            .setShowWhen(true)
            .setContentIntent(openOnMap)
            .setAutoCancel(false)
            .setDeleteIntent(broadcast(context, REQUEST_CODE_DISMISS, dismiss))
            .addAction(0, context.getString(R.string.notification_checkin_action_check_in), broadcast(context, REQUEST_CODE_CHECK_IN, checkIn))
            .addAction(0, context.getString(R.string.notification_checkin_action_dismiss), broadcast(context, REQUEST_CODE_DISMISS, dismiss))
            .setTimeoutAfter(millisUntilMidnight(postedAt))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(poi.id, PROMPT_ID, notification)
        return true
    }

    /** Replaces [placeId]'s prompt with "Checked in" + Undo for the just-logged [visitId]. */
    fun showConfirmation(context: Context, placeId: String, placeName: String?, visitId: Long, visitedAt: Long) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(placeId, PROMPT_ID)
        if (!manager.areNotificationsEnabled()) return

        val undo = receiverIntent(context, GeofenceBroadcastReceiver.ACTION_UNDO_CHECK_IN, placeId)
            .putExtra(GeofenceBroadcastReceiver.EXTRA_VISIT_ID, visitId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_checkin)
            .setContentTitle(context.getString(R.string.notification_checked_in_title, placeName(context, placeName)))
            .setContentText(context.getString(R.string.notification_checked_in_body, formatTime(context, visitedAt)))
            .setWhen(visitedAt)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, context.getString(R.string.notification_checked_in_action_undo), broadcast(context, REQUEST_CODE_UNDO, undo))
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MILLIS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        manager.notify(placeId, CONFIRMATION_ID, notification)
    }

    fun cancelPrompt(context: Context, placeId: String) {
        NotificationManagerCompat.from(context).cancel(placeId, PROMPT_ID)
    }

    fun cancelConfirmation(context: Context, placeId: String) {
        NotificationManagerCompat.from(context).cancel(placeId, CONFIRMATION_ID)
    }

    /** Place ids of the prompts currently on screen. */
    fun promptedPlaceIds(context: Context): Set<String> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == PROMPT_ID && it.tag != null }
            .map { it.tag }
            .toSet()

    private fun receiverIntent(context: Context, action: String, placeId: String): Intent =
        Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(action)
            .setData(placeUri(placeId))
            .putExtra(GeofenceBroadcastReceiver.EXTRA_PLACE_ID, placeId)

    private fun broadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun placeUri(placeId: String): Uri =
        Uri.Builder().scheme("freizeit").authority("checkin").appendPath(placeId).build()

    private fun placeName(context: Context, name: String?): String =
        name ?: context.getString(R.string.checkin_history_unnamed_place)

    private fun formatTime(context: Context, millis: Long): String =
        DateFormat.getTimeFormat(context).format(Date(millis))

    private const val REQUEST_CODE_CHECK_IN = 2
    private const val REQUEST_CODE_DISMISS = 3
    private const val REQUEST_CODE_OPEN_ON_MAP = 4
    private const val REQUEST_CODE_UNDO = 5
}
