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

package studio.lunabee.feedback.core.metadata

import studio.lunabee.feedback.core.config.LBFeedbackConfig
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import kotlin.time.Clock

/**
 * Collects the [LBFeedbackMetadata] of a feedback: device, OS, locale, app info from [config], library version and
 * send time.
 */
class LBFeedbackDefaultMetadataProvider(
    private val config: LBFeedbackConfig,
    private val clock: Clock = Clock.System,
) : LBFeedbackMetadataProvider {
    override suspend fun collect(): LBFeedbackMetadata {
        val platform = readPlatformInfo()
        return LBFeedbackMetadata(
            appVersionName = config.appVersionName,
            appVersionCode = config.appVersionCode,
            buildVariant = config.buildVariant,
            deviceManufacturer = platform.deviceManufacturer,
            deviceModel = platform.deviceModel,
            osVersion = platform.osVersion,
            locale = platform.locale,
            libraryVersion = FeedbackCoreVersion,
            sentAt = clock.now(),
            extras = config.extras(),
        )
    }
}
