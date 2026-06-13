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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single process-wide [DataStore] backing every [SyncTimestampStore]. The `preferencesDataStore`
 * delegate guarantees a single instance per process for this file name, which AndroidX requires (it
 * throws if two stores point at the same file). The base name matches the legacy SharedPreferences
 * file name `com.lunabee.lbsynchronization` so existing installs keep the same on-disk location.
 */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "com.lunabee.lbsynchronization")

/**
 * Returns a [SyncTimestampStore] backed by the single process-wide [syncDataStore]. Built from the
 * application context so the same store is shared between [studio.lunabee.synchronization.syncmanager.LBSyncManager]
 * instances and [studio.lunabee.synchronization.LBSyncOperator].
 */
internal val Context.syncTimestampStore: SyncTimestampStore
    get() = SyncTimestampStore(applicationContext.syncDataStore)
