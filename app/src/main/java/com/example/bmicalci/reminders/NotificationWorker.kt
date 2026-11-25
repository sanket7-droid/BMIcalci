package com.example.bmicalci.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bmicalci.MainActivity
import java.util.Calendar

class NotificationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Quiet hours: 12:00 AM - 7:00 AM
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7) return Result.success()

        sendHydrationNotification()
        return Result.success()
    }

    private fun sendHydrationNotification() {
        val ctx = applicationContext
        // Without the runtime permission (Android 13+) or if the user disabled them, skip quietly.
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return

        // Channel settings are locked once created, so a new sound requires a new channel id.
        val channelId = "hydration_sound_channel"
        val soundUri = Uri.parse("android.resource://${ctx.packageName}/raw/water_drop")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Hydration Reminders", NotificationManager.IMPORTANCE_HIGH)
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            channel.setSound(soundUri, attrs)
            manager.createNotificationChannel(channel)
        }

        // Tapping the notification opens the app so the user can log water.
        val openApp = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle("Pani Pine Ka Time Hogaya")
            .setContentText("Keep your metabolism burning efficiently. Tap to log your glass of water!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setSound(soundUri)

        try {
            NotificationManagerCompat.from(ctx).notify(1, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
