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
 * How the managers of one [LBSyncGroup] are run. Either way every manager attempts and the group's
 * result aggregation is the same: a failing manager never stops the others.
 */
enum class LBSyncExecutionMode {
    /**
     * All managers of the group run concurrently. The default, and the only behaviour before this
     * setting existed.
     */
    Parallel,

    /**
     * Managers run one after another, in [LBSyncGroup.syncManagers] iteration order — use it when the
     * managers of a group must not hit the server at the same time. A dependency between managers is
     * better modelled as two groups, which the operator already runs in registration order.
     */
    Sequential,
}
