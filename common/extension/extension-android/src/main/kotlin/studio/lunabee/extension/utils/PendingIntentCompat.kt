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

package studio.lunabee.extension.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.IntDef

@SuppressLint("InlinedApi")
@IntDef(
    flag = true,
    value = [
        PendingIntent.FLAG_ONE_SHOT,
        PendingIntent.FLAG_NO_CREATE,
        PendingIntent.FLAG_CANCEL_CURRENT,
        PendingIntent.FLAG_UPDATE_CURRENT,
        PendingIntent.FLAG_IMMUTABLE,
        PendingIntent.FLAG_MUTABLE,
        Intent.FILL_IN_ACTION,
        Intent.FILL_IN_DATA,
        Intent.FILL_IN_CATEGORIES,
        Intent.FILL_IN_COMPONENT,
        Intent.FILL_IN_PACKAGE,
        Intent.FILL_IN_SOURCE_BOUNDS,
        Intent.FILL_IN_SELECTOR,
        Intent.FILL_IN_CLIP_DATA,
    ],
)
@Retention(AnnotationRetention.SOURCE)
annotation class Flags

@SuppressLint("UnspecifiedImmutableFlag")
object PendingIntentCompat {
    fun getActivityImmutable(context: Context, requestCode: Int, intent: Intent, @Flags flags: Int = 0): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(context, requestCode, intent, flags)
        }

    fun getBroadcastImmutable(context: Context, requestCode: Int, intent: Intent, @Flags flags: Int): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

    fun getBroadcastMutable(context: Context, requestCode: Int, intent: Intent, @Flags flags: Int): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

    fun getActivityMutable(context: Context, requestCode: Int, intent: Intent, @Flags flags: Int): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }
}
