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

import kotlin.concurrent.Volatile

/**
 * Process-wide registry for the single [SyncTimestampStore] backend the engine uses.
 *
 * The engine is storage-agnostic: it never constructs a store, it reads the installed one. A backend
 * module (`synchronization-core-datastore`, `synchronization-core-room`, …) exposes a one-line factory;
 * the app calls [install] once at startup:
 *
 * ```kotlin
 * // Android, DataStore backend
 * LBSyncStorage.install(context.dataStoreSyncTimestampStore())
 * ```
 *
 * There is no automatic classpath wiring: the Android multiplatform library format merges no component
 * manifest and iOS has no classpath init, so a single explicit [install] is the portable contract.
 * Managers built with the no-store constructor read [requireStore] lazily, so [install] only has to run
 * before the first synchronization, not before the managers are created.
 */
object LBSyncStorage {

    @Volatile
    private var store: SyncTimestampStore? = null

    /**
     * Installs [store] as the process-wide backend, replacing any previously installed one. Call once at
     * startup. Installing a second time (e.g. both backend modules on the classpath) is last-wins.
     */
    fun install(store: SyncTimestampStore) {
        this.store = store
    }

    /**
     * The installed backend, or `null` when none has been installed yet.
     */
    val installedStore: SyncTimestampStore?
        get() = store

    /**
     * The installed backend.
     *
     * @throws IllegalStateException when no backend has been installed. Add a `synchronization-core-*`
     * backend module and call [install] at startup.
     */
    fun requireStore(): SyncTimestampStore = store ?: error(
        "No SyncTimestampStore installed. Add a synchronization-core-datastore or " +
            "synchronization-core-room dependency and call LBSyncStorage.install(...) at startup.",
    )
}
