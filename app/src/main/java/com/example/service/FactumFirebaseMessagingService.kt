package com.example.service

import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FactumFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM registration token: $token")
        // Token refreshed - could send to server or save locally
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        val data = remoteMessage.data
        val titleFromData = data["title"]
        val bodyFromData = data["body"] ?: data["message"]
        val linkFromData = data["link"] ?: data["url"]
        val regionFromData = data["region"]

        // Check if message contains notification payload
        val notificationTitle = remoteMessage.notification?.title
        val notificationBody = remoteMessage.notification?.body

        val finalTitle = notificationTitle ?: titleFromData ?: "Breaking News Alert"
        val finalBody = notificationBody ?: bodyFromData ?: "New important story detected in your feed."

        NotificationHelper.sendBreakingNewsNotification(
            context = applicationContext,
            title = finalTitle,
            body = finalBody,
            articleUrl = linkFromData,
            region = regionFromData
        )
    }

    companion object {
        private const val TAG = "FactumFCMService"
    }
}
