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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class LBFeedbackAttachmentTest {
    @Test
    fun screenshotName_is_utc_and_filename_safe() {
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-19T13:47:39.123456Z")
        }

        assertEquals("screenshot-20261119-134739.jpg", LBFeedbackAttachment.screenshotName(clock))
    }

    @Test
    fun screenshotName_without_fraction() {
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-01-02T03:04:05Z")
        }

        assertEquals("screenshot-20260102-030405.jpg", LBFeedbackAttachment.screenshotName(clock))
    }
}
