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

package studio.lunabee.parse.query

import kotlin.time.Instant

/**
 * Page of a pull.
 *
 * @property items Objects of the page, after the transform of the pull.
 * @property maxUpdatedAt Latest update among the objects fetched, including those the transform left out.
 * @property nextCursor Cursor to pass to the next pull, `null` when this page is the last one.
 */
data class ParsePage<T>(
    val items: List<T>,
    val maxUpdatedAt: Instant?,
    val nextCursor: String?,
)
