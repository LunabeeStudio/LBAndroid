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

/**
 * Raised when an [LBSyncOperator] sync request is made from inside a sync manager's own engine callback
 * (`fetchRequest`, `pushObjectsToServer`, …). The operator's sync lock is already held by the run that
 * is executing that callback and the lock is not reentrant, so honouring the request would deadlock the
 * whole operator — every later sync request, event-triggered refresh included, would queue behind it for
 * the lifetime of the process. The request fails fast with this exception instead.
 *
 * Fixes, in order of preference: model the dependency as an earlier [LBSyncGroup] (groups run
 * sequentially, in registration order), or launch the request on a scope of your own
 * (`myScope.launch { LBSyncOperator.sync(…) }`) so it queues behind the current run instead of inside it.
 *
 * @property target the sync request that was refused.
 */
class LBSyncReentrantCallException(
    val target: String,
) : IllegalStateException(
    "LBSyncOperator. was called from inside a sync manager callback, which would deadlock the " +
        "operator sync lock. Model the dependency as an earlier LBSyncGroup, or launch the request on " +
        "your own scope.",
)
