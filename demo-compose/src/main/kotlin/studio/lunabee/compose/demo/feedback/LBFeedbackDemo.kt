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

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import studio.lunabee.compose.BuildConfig
import studio.lunabee.feedback.core.config.LBFeedbackConfig
import studio.lunabee.feedback.core.store.lbFeedbackStore
import studio.lunabee.feedback.core.transport.LBFeedbackTransport
import studio.lunabee.feedback.plane.transport.LBPlaneFeedbackTransport
import studio.lunabee.feedback.ui.LBFeedback
import studio.lunabee.feedback.ui.install

object LBFeedbackDemo {
    val fakeTransport: FakeFeedbackTransport = FakeFeedbackTransport()

    val usesPlane: Boolean = listOf(
        BuildConfig.FEEDBACK_PLANE_TOKEN,
        BuildConfig.FEEDBACK_PLANE_BASE_URL,
        BuildConfig.FEEDBACK_PLANE_WORKSPACE,
        BuildConfig.FEEDBACK_PLANE_PROJECT_ID,
    ).all { it.isNotBlank() }

    fun install(application: Application) {
        val transport: LBFeedbackTransport = if (usesPlane) {
            LBPlaneFeedbackTransport(
                baseUrl = BuildConfig.FEEDBACK_PLANE_BASE_URL,
                workspaceSlug = BuildConfig.FEEDBACK_PLANE_WORKSPACE,
                projectId = BuildConfig.FEEDBACK_PLANE_PROJECT_ID,
                token = { BuildConfig.FEEDBACK_PLANE_TOKEN },
                httpClient = HttpClient(Android),
            )
        } else {
            fakeTransport
        }
        LBFeedback.install(
            application = application,
            config = LBFeedbackConfig(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                buildVariant = BuildConfig.BUILD_TYPE,
            ),
            transport = transport,
            store = application.lbFeedbackStore(),
        )
    }
}
