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

import studio.lunabee.synchronization.utils.DateTimeFormatter
import java.util.Date

/**
 * There is the list of different status a sync manager can have
 * Some of them can contains sync date, processed object count or error
 */
@Suppress("unused")
sealed class LBSyncProcessStatus {

    object NeverSync : LBSyncProcessStatus()

    object PendingSync : LBSyncProcessStatus()

    class SyncSuccessfully(paramLastSuccessfulSync: Date) : LBSyncProcessStatus() {
        var lastSuccessfulSync: Date = paramLastSuccessfulSync
            get() = field.coerceAtMost(Date())
    }

    object Disabled : LBSyncProcessStatus()

    class UploadStarted(var at: Date) : LBSyncProcessStatus()

    class UploadFinishSuccessfully(
        var processedObjectCount: Int,
        var at: Date,
    ) : LBSyncProcessStatus()

    class UploadFinishWithError(var error: Exception, var at: Date) : LBSyncProcessStatus()

    class DownloadStarted(var at: Date) : LBSyncProcessStatus()

    class DownloadUpdated(var processedObjectCount: Int, var at: Date) : LBSyncProcessStatus()

    class DownloadFinishSuccessfully(var at: Date) : LBSyncProcessStatus()

    class DownloadFinishWithError(var error: Exception, var at: Date) : LBSyncProcessStatus()

    /**
     * @return the status description
     * Helpful for debugging
     */
    fun fullDescription(): String {
        return when (this) {
            is NeverSync -> "Never synchronized"

            is PendingSync -> "Waiting for synchronization"

            is SyncSuccessfully -> "Updated ${DateTimeFormatter.format(this.lastSuccessfulSync)}"

            is Disabled -> "Disabled due to isEnabled closures"

            is UploadStarted -> "Uploading..."

            is UploadFinishSuccessfully ->
                "${this.processedObjectCount} records uploaded successfully ${
                    DateTimeFormatter.format(
                        this.at,
                    )
                })"

            is UploadFinishWithError -> "Upload failed ${DateTimeFormatter.format(this.at)}"

            is DownloadStarted -> "Downloading..."

            is DownloadUpdated ->
                "${this.processedObjectCount} records have been downloaded successfully ${
                    DateTimeFormatter.format(
                        this.at,
                    )
                }"

            is DownloadFinishSuccessfully ->
                "Download finish successfully ${
                    DateTimeFormatter.format(
                        this.at,
                    )
                }"

            is DownloadFinishWithError -> "Download failed ${DateTimeFormatter.format(this.at)}"
        }
    }

    /**
     * @return if the sync manager owner of the status is still processing or not
     * **WARNING** : UploadFinishSuccessfully and DownloadFinishSuccessfully return true
     * because there are just steps of the full synchronization
     */
    fun isProcessing(): Boolean {
        return when (this) {
            is NeverSync -> false
            is PendingSync -> false
            is SyncSuccessfully -> false
            is Disabled -> false
            is UploadStarted -> true
            is UploadFinishSuccessfully -> true
            is UploadFinishWithError -> false
            is DownloadStarted -> true
            is DownloadUpdated -> true
            is DownloadFinishSuccessfully -> true
            is DownloadFinishWithError -> false
        }
    }

    /**
     * @return the status error if it contains one, null otherwise
     */
    fun currentError(): Exception? {
        return when (this) {
            is UploadFinishWithError -> this.error
            is DownloadFinishWithError -> this.error
            else -> null
        }
    }
}
