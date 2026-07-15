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

package studio.lunabee.synchronization.store

import kotlin.jvm.JvmInline

/**
 * Persisted identity of a sync manager's cursors in a [SyncTimestampStore]. Wraps the manager's
 * `syncKey`; treat it as a stable persisted key (renaming it loses the saved cursor).
 */
@JvmInline
value class SyncKey(val value: String)
