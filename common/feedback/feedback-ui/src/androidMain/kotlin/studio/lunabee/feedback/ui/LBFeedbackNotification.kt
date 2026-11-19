/*
 * Copyright (c) 2026 Lunabee Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.lunabee.feedback.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Persistent notification whose tap opens the feedback form, with a capture of the app screen. Off until [show] is
 * called.
 */
object LBFeedbackNotification {
    private const val ChannelId: String = "studio.lunabee.feedback.ui"
    private const val NotificationId: Int = 0x1BFB

    /**
     * Posts the ongoing notification. The library never asks for the permission: on API 33 and above the app must hold
     * `POST_NOTIFICATIONS`, otherwise nothing is shown.
     */
    fun show(context: Context) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        createChannel(context)
        val intent = PendingIntent.getActivity(
            context,
            0,
            LBFeedbackActivity.intent(context, startOnList = false, capture = true).withNewTaskIfNeeded(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_lb_feedback_notification)
            .setContentTitle(context.getString(R.string.lb_feedback_notification_title))
            .setContentText(context.getString(R.string.lb_feedback_notification_text))
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NotificationId, notification)
    }

    /**
     * Removes the notification.
     */
    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NotificationId)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            context.getString(R.string.lb_feedback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
