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
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {
    const val CHANNEL_ID = "breaking_news_channel"
    const val CHANNEL_NAME = "Breaking News Alerts"
    const val CHANNEL_DESC = "Real-time personalized push notifications for breaking news and blindspot detection."

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

        val intent = if (!articleUrl.isNullOrEmpty() && (articleUrl.startsWith("http://") || articleUrl.startsWith("https://"))) {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } catch (e: Exception) {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
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
        val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt() % 10000
        notificationManager.notify(notificationId, builder.build())
    }

    fun syncFcmRegionTopics(region: String) {
        syncFcmRegionTopics(null, region)
    }

    fun syncFcmRegionTopics(context: Context?, region: String) {
        if (context != null && !isFirebaseAvailable(context)) {
            Log.i("NotificationHelper", "FirebaseApp is not initialized. FCM features disabled.")
            return
        }
        try {
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
        } catch (e: Exception) {
            Log.i("NotificationHelper", "FCM disabled or not initialized: ${e.message}")
        }
    }

    fun fetchFcmToken(onTokenRetrieved: (String) -> Unit) {
        fetchFcmToken(null, onTokenRetrieved)
    }

    fun fetchFcmToken(context: Context?, onTokenRetrieved: (String) -> Unit) {
        if (context != null && !isFirebaseAvailable(context)) {
            Log.i("NotificationHelper", "FirebaseApp is not initialized. FCM features disabled.")
            return
        }
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result
                    Log.d("NotificationHelper", "FCM Token: $token")
                    onTokenRetrieved(token)
                } else {
                    Log.w("NotificationHelper", "Fetching FCM registration token failed", task.exception)
                }
            }
        } catch (e: Exception) {
            Log.i("NotificationHelper", "FCM disabled or not initialized: ${e.message}")
        }
    }

    private fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
