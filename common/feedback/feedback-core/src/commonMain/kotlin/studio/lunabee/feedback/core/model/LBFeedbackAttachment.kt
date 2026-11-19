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

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private val ScreenshotTimeFormat = LocalDateTime.Format {
    year()
    monthNumber()
    day()
    char('-')
    hour()
    minute()
    second()
}

/**
 * File sent with a feedback, held in memory only. It reaches the remote queue as it is: it must never carry user data.
 *
 * @property bytes file content
 * @property name file name, with extension
 * @property mimeType media type of the file, for example `image/png`
 */
class LBFeedbackAttachment(
    val bytes: ByteArray,
    val name: String,
    val mimeType: String,
) {
    companion object {
        /** Largest size of one attachment, in bytes. */
        const val MaxBytes: Int = 5 * 1024 * 1024

        /**
         * File name of a screenshot taken at the time of [clock], like `screenshot-20261119-134739.jpg` (UTC).
         */
        fun screenshotName(clock: Clock = Clock.System): String {
            val stamp = clock.now().toLocalDateTime(TimeZone.UTC).format(ScreenshotTimeFormat)
            return "screenshot-$stamp.jpg"
        }
    }
}
