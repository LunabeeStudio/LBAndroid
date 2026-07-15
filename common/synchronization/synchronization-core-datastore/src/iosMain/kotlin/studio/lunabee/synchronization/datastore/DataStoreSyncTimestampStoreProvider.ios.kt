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

package studio.lunabee.synchronization.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import studio.lunabee.synchronization.store.SyncTimestampStore

/**
 * Returns a [SyncTimestampStore] backed by a DataStore file in the app's documents directory.
 *
 * Install it once at startup:
 * ```kotlin
 * LBSyncStorage.install(dataStoreSyncTimestampStore())
 * ```
 */
@OptIn(ExperimentalForeignApi::class)
fun dataStoreSyncTimestampStore(): SyncTimestampStore {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val filePath = "${requireNotNull(documentDirectory?.path)}/$SyncDataStoreName.preferences_pb"
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(produceFile = { filePath.toPath() })
    return DataStoreSyncTimestampStore(dataStore)
}
