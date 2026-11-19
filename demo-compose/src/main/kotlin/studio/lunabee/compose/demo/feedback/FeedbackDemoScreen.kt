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

package studio.lunabee.compose.demo.feedback

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.ui.LBFeedback
import studio.lunabee.feedback.ui.LBFeedbackNotification
import studio.lunabee.feedback.ui.LBFeedbackShakeTrigger
import studio.lunabee.feedback.ui.open

@Composable
fun FeedbackDemoScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    var simulateError by remember { mutableStateOf(LBFeedbackDemo.fakeTransport.simulateNetworkError) }
    var shakeEnabled by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) LBFeedbackNotification.show(application)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (LBFeedbackDemo.usesPlane) "Transport: Plane (local.properties)" else "Transport: fake (in memory)")
        Button(onClick = { LBFeedback.open(context) }) { Text("Send feedback") }
        Button(onClick = { LBFeedback.open(context, startOnList = true) }) { Text("My feedback") }
        Button(
            onClick = {
                LBFeedback.addAttachment(
                    LBFeedbackAttachment(
                        bytes = "Sample log line shared by the demo app".encodeToByteArray(),
                        name = "demo-log.txt",
                        mimeType = "text/plain",
                    ),
                )
            },
        ) {
            Text("Attach a sample file")
        }
        Button(onClick = { FlagSecureActivity.startActivity(context) }) { Text("Open FLAG_SECURE screen") }
        Button(
            onClick = {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    LBFeedbackNotification.show(application)
                }
            },
        ) {
            Text("Show notification")
        }
        Button(onClick = { LBFeedbackNotification.hide(application) }) { Text("Hide notification") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = shakeEnabled,
                onCheckedChange = {
                    shakeEnabled = it
                    if (it) LBFeedbackShakeTrigger.enable(application) else LBFeedbackShakeTrigger.disable()
                },
            )
            Text("Shake to send feedback")
        }
        if (!LBFeedbackDemo.usesPlane) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = simulateError,
                    onCheckedChange = {
                        simulateError = it
                        LBFeedbackDemo.fakeTransport.simulateNetworkError = it
                    },
                )
                Text("Simulate network error")
            }
            Button(onClick = LBFeedbackDemo.fakeTransport::triage) { Text("Simulate triage (cycle statuses)") }
        }
    }
}
