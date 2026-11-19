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

package studio.lunabee.feedback.core.capture

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.view.drawToBitmap
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import java.lang.ref.WeakReference

actual object LBFeedbackScreenCapture {
    private val encoder: LBFeedbackBitmapDataSource = LBFeedbackBitmapDataSource()
    private var registered: Boolean = false
    private var current: WeakReference<Activity>? = null

    /**
     * Whether an activity marked with [LBFeedbackCaptureExcluded] is resumed.
     */
    var isExcludedActivityResumed: Boolean = false
        private set

    /**
     * Starts tracking the resumed activity. Safe to call more than once.
     */
    fun install(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(Tracker())
    }

    /**
     * Draws the tracked activity into a bitmap. Call it from the main thread. Returns `null` without a tracked activity,
     * or when the view cannot be drawn. Pass the bitmap to [encode].
     */
    fun snapshot(): Bitmap? {
        val decorView = current?.get()?.window?.decorView ?: return null
        return try {
            decorView.drawToBitmap()
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Encodes [bitmap] as a JPEG attachment under [LBFeedbackAttachment.MaxBytes], off the main thread.
     */
    suspend fun encode(bitmap: Bitmap): LBFeedbackAttachment = encoder.encode(bitmap)

    actual suspend fun capture(): LBFeedbackAttachment? = snapshot()?.let { encode(it) }

    private class Tracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is LBFeedbackCaptureExcluded) {
                isExcludedActivityResumed = true
            } else {
                current = WeakReference(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is LBFeedbackCaptureExcluded) isExcludedActivityResumed = false
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (current?.get() === activity) current = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
