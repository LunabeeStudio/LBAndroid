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

package studio.lunabee.synchronization

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus

/**
 * Reduces a combined status map to the single boolean every aggregate view of [LBSyncGroup] and
 * [LBSyncOperator] publishes: `true` while [predicate] holds for at least one member, consecutive
 * duplicates dropped.
 *
 * @param predicate the per-member test, [LBSyncProcessStatus.isProcessing] or
 * [LBSyncProcessStatus.isActive].
 * @return a flow of the aggregate state.
 */
internal fun Flow<Map<SyncKey, LBSyncProcessStatus>>.anyStatus(
    predicate: (LBSyncProcessStatus) -> Boolean,
): Flow<Boolean> = map { statuses -> statuses.values.any(predicate) }
    .distinctUntilChanged()
