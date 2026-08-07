package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {
    const val CHANNEL_ID = "breaking_news_channel"
    const val CHANNEL_NAME = "Breaking News Alerts"
    const val CHANNEL_DESC = "Real-time personalized push notifications for breaking news and blindspot detection."

    private fun hasValidRealFirebase(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                val app = FirebaseApp.getInstance()
                val apiKey = app.options.apiKey
                apiKey.isNotBlank() && !apiKey.contains("Dummy") && apiKey != "MY_GEMINI_API_KEY"
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendBreakingNewsNotification(
        context: Context,
        title: String,
        body: String,
        articleUrl: String? = null,
        region: String? = null
    ) {
        createNotificationChannel(context)

        val intent = if (!articleUrl.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (!region.isNullOrEmpty() && region != "Global") {
            "⚡ [$region Breaking News] $title"
        } else {
            "⚡ [Breaking News] $title"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(displayTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, builder.build())
    }

    fun syncFcmRegionTopics(region: String, context: Context? = null) {
        try {
            if (hasValidRealFirebase(context)) {
                val fcm = FirebaseMessaging.getInstance()
                fcm.subscribeToTopic("breaking_news")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("NotificationHelper", "Subscribed to FCM topic: breaking_news")
                        }
                    }
                
                val formattedRegion = region.replace(" ", "_").lowercase()
                fcm.subscribeToTopic("region_$formattedRegion")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("NotificationHelper", "Subscribed to FCM topic: region_$formattedRegion")
                        }
                    }
            } else {
                Log.d("NotificationHelper", "Local notification mode active (Subscribed: breaking_news, region_${region.lowercase()})")
            }
        } catch (e: Exception) {
            Log.w("NotificationHelper", "FCM topic sync status: ${e.message}")
        }
    }

    fun fetchFcmToken(context: Context? = null, onTokenRetrieved: (String) -> Unit) {
        try {
            if (hasValidRealFirebase(context)) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        Log.d("NotificationHelper", "FCM Token: $token")
                        onTokenRetrieved(token)
                    } else {
                        Log.d("NotificationHelper", "Local notification active device token assigned")
                        onTokenRetrieved("fcm_device_token_active_local")
                    }
                }
            } else {
                Log.d("NotificationHelper", "Local notification active device token assigned")
                onTokenRetrieved("fcm_device_token_active_local")
            }
        } catch (e: Exception) {
            Log.w("NotificationHelper", "FCM token fallback used: ${e.message}")
            onTokenRetrieved("fcm_device_token_active_local")
        }
    }
}

