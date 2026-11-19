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

package studio.lunabee.feedback.core.model

import kotlin.time.Instant

/**
 * Technical context collected automatically with each feedback.
 *
 * @property appVersionName version name of the host app
 * @property appVersionCode version code of the host app
 * @property buildVariant build variant of the host app
 * @property deviceManufacturer maker of the device
 * @property deviceModel model of the device
 * @property osVersion operating system and version
 * @property locale current locale as a language tag
 * @property libraryVersion version of the feedback library
 * @property sentAt time the feedback was sent, in UTC
 * @property extras fields supplied by the app, see [studio.lunabee.feedback.core.config.LBFeedbackConfig.extras]
 */
data class LBFeedbackMetadata(
    val appVersionName: String,
    val appVersionCode: Long,
    val buildVariant: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val libraryVersion: String,
    val sentAt: Instant,
    val extras: Map<String, String> = emptyMap(),
)
