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

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import studio.lunabee.feedback.core.capture.LBFeedbackScreenCapture
import kotlin.math.sqrt

private const val ShakeThresholdG: Float = 2.7f
private const val DebounceMillis: Long = 1_000L

/**
 * Opens the feedback form when the user shakes the device. Off until [enable] is called. It listens to the accelerometer
 * only while the app is in the foreground.
 */
object LBFeedbackShakeTrigger {
    private var registration: Registration? = null

    /**
     * Starts listening. Call it from the main thread, after [LBFeedback.install]. Calling it again while enabled does
     * nothing.
     */
    fun enable(application: Application) {
        if (registration != null) return
        val sensors = application.getSystemService(SensorManager::class.java) ?: return
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = ShakeListener { LBFeedback.open(application) }
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }

            override fun onStop(owner: LifecycleOwner) {
                sensors.unregisterListener(listener)
            }
        }
        registration = Registration(sensors, listener, observer)
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    /**
     * Stops listening. Call it from the main thread.
     */
    fun disable() {
        val current = registration ?: return
        ProcessLifecycleOwner.get().lifecycle.removeObserver(current.observer)
        current.sensors.unregisterListener(current.listener)
        registration = null
    }

    private class Registration(
        val sensors: SensorManager,
        val listener: SensorEventListener,
        val observer: DefaultLifecycleObserver,
    )

    private class ShakeListener(private val onShake: () -> Unit) : SensorEventListener {
        private var lastShake: Long = 0L

        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            val now = SystemClock.elapsedRealtime()
            val debounced = now - lastShake > DebounceMillis
            if (gForce > ShakeThresholdG && debounced && !LBFeedbackScreenCapture.isExcludedActivityResumed) {
                lastShake = now
                onShake()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
