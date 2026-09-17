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

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element the engine installs around a manager's pipeline, so [LBSyncOperator] can
 * tell a request coming from inside a run (which would deadlock its non-reentrant lock) from a legit
 * external one.
 *
 * Context inheritance draws exactly the right line: a manager callback runs in the marked coroutine, and
 * so does anything it `withContext`s or structurally launches — all deadlocking, all refused. A
 * fire-and-forget request launched on an unrelated scope does not inherit the marker and stays allowed.
 */
internal class SyncEngineMarker : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SyncEngineMarker>
}
