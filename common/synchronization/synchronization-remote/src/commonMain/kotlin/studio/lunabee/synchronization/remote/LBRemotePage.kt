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

import kotlin.time.Instant

/**
 * One page of objects read from a remote backend, oldest update first.
 *
 * @param T the local model the page objects are mapped to.
 * @property objects the page objects, mapped to the local model. Records the backend returned but that cannot be
 * stored (e.g. without a usable id) are left out.
 * @property maxUpdatedAt newest `updatedAt` among every record the backend returned for the page, the left-out ones
 * included, so they still move the incremental cursor. Required when [objects] is not empty, `null` when the page is
 * empty.
 * @property nextCursor the keyset position of the last record the backend returned for the page, passed to
 * [LBPullRemoteDataSource.fetchPage] to read the next page, or `null` when no further page holds objects.
 * @throws IllegalArgumentException when [objects] is not empty and [maxUpdatedAt] is `null`.
 */
data class LBRemotePage<T>(
    val objects: List<T>,
    val maxUpdatedAt: Instant?,
    val nextCursor: String?,
) {
    /** `true` when a further page holds objects, i.e. [nextCursor] is set. */
    val hasNextPage: Boolean get() = nextCursor != null

    init {
        require(objects.isEmpty() || maxUpdatedAt != null) {
            "A page holding objects must set maxUpdatedAt: it is the only source of the incremental cursor"
        }
    }
}
