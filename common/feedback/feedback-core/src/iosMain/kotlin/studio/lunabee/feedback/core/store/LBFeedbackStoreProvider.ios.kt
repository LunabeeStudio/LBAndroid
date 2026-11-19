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

package studio.lunabee.feedback.core.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private val sharedStore: LBFeedbackStore by lazy { createStore() }

/**
 * Returns the process-wide [LBFeedbackStore], backed by a DataStore file in the app's application support directory.
 *
 * Safe to call repeatedly: every call returns the same instance.
 */
fun lbFeedbackStore(): LBFeedbackStore = sharedStore

@OptIn(ExperimentalForeignApi::class)
private fun createStore(): LBFeedbackStore {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val filePath = "${requireNotNull(documentDirectory?.path)}/$FeedbackDataStoreName.preferences_pb"
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(produceFile = { filePath.toPath() })
    return DataStoreFeedbackStore(dataStore)
}
