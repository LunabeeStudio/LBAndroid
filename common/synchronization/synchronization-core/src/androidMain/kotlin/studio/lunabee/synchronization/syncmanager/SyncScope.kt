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

package studio.lunabee.synchronization.syncmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Shared, library-owned [CoroutineScope] every sync manager runs in when constructed via the
 * `Context`-based convenience constructor. It is the coroutine replacement for the old detached
 * `GlobalScope.launch`: receiver-triggered syncs and automatic retries keep firing independently of any
 * caller scope. A [SupervisorJob] keeps one failing sync from tearing the others down, and
 * [Dispatchers.IO] matches the previous I/O-bound execution context.
 *
 * The operator reuses this same scope for receiver-triggered launches.
 */
internal val defaultSyncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
