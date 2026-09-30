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

package studio.lunabee.synchronization.remote

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LBRemotePageTest {
    @Test
    fun a_page_holding_objects_without_max_updated_at_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            LBRemotePage(objects = listOf(Item("a")), maxUpdatedAt = null, nextCursor = null)
        }
    }

    @Test
    fun an_empty_page_may_have_no_max_updated_at() {
        val page = LBRemotePage<Item>(objects = emptyList(), maxUpdatedAt = null, nextCursor = null)

        assertNull(page.maxUpdatedAt)
    }
}
