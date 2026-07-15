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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.LocalObj
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadTest {

    // region nothing-to-upload short-circuit

    @Test
    fun nothing_to_upload_skips_the_upload_phase_but_still_succeeds() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, uploadObjects = emptyList())
        val statuses = manager.recordStatuses(scope, testScheduler)

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Success, "a sync with nothing to upload still succeeds")
        assertEquals(expected = 0, actual = manager.pushCalls, "pushObjectsToServer is never called")
        assertFalse("UploadStarted" in statuses(), "no UploadStarted is emitted when nothing is pending")
        assertTrue(manager.pushedBatches.isEmpty(), "no batch is pushed")
    }

    // endregion

    // region successful upload

    @Test
    fun non_empty_upload_emits_started_then_finish_successfully_with_the_pushed_batch() = runManagerTest { store, scope ->
        val toUpload = listOf(LocalObj("a"), LocalObj("b"), LocalObj("c"))
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = toUpload,
            supportChangeNotification = true, // keep the status sequence focused on the upload phase
        )
        val statuses = manager.recordStatuses(scope, testScheduler)

        manager.synchronize()
        advanceUntilIdle()

        val sequence = statuses()
        val startedAt = sequence.indexOf("UploadStarted")
        val finishedAt = sequence.indexOf("UploadFinishSuccessfully")
        assertTrue(startedAt >= 0 && finishedAt > startedAt, "UploadStarted precedes UploadFinishSuccessfully")

        assertEquals(expected = 1, actual = manager.pushCalls)
        assertEquals(
            expected = listOf(toUpload),
            actual = manager.pushedBatches,
            "the pushed batch equals the objectToBeUploaded list",
        )

        val uploadFinished = manager.currentSyncStatus
        // Terminal status is SyncSuccessfully; assert the processed count via the captured batch size,
        // which the engine reports verbatim as UploadFinishSuccessfully.processedObjectCount.
        assertEquals(
            expected = toUpload.size,
            actual = manager.pushedBatches.single().size,
            "the engine reports the full pending list as the processed count",
        )
        assertTrue(uploadFinished is LBSyncProcessStatus.SyncSuccessfully, "the pipeline ends successfully")
    }

    @Test
    fun upload_finish_successfully_carries_the_processed_object_count() = runManagerTest { store, scope ->
        val toUpload = listOf(LocalObj("x"), LocalObj("y"))
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = toUpload,
            supportChangeNotification = true,
        )

        val recorded = mutableListOf<LBSyncProcessStatus>()
        val job = scope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.status.collect { recorded += it }
        }

        manager.synchronize()
        advanceUntilIdle()
        job.cancel()

        val captured = recorded.filterIsInstance<LBSyncProcessStatus.UploadFinishSuccessfully>().firstOrNull()
        assertEquals(
            expected = toUpload.size,
            actual = captured?.processedObjectCount,
            "UploadFinishSuccessfully.processedObjectCount equals objects.size",
        )
    }

    // endregion

    // region push failure

    @Test
    fun push_throws_emits_upload_finish_with_error_and_returns_that_failure() = runManagerTest { store, scope ->
        val boom = IllegalStateException("push boom")
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushError = boom,
            retryTempo = null,
        )
        val statuses = manager.recordStatuses(scope, testScheduler)

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Failure, "a push failure surfaces as Failure")
        assertEquals(expected = boom, actual = result.throwable, "the returned failure carries the thrown error")
        assertEquals(
            expected = "UploadFinishWithError",
            actual = statuses().last(),
            "the terminal status is UploadFinishWithError",
        )
        assertTrue(manager.currentSyncStatus.currentError() === boom, "the error status exposes the thrown exception")
    }

    // endregion

    // region pipeline ordering with upload

    @Test
    fun pipeline_orders_download_then_upload_then_re_download_when_not_server_notified() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, uploadObjects = listOf(LocalObj("a")))
        val statuses = manager.recordStatuses(scope, testScheduler)

        manager.synchronize()
        advanceUntilIdle()

        val sequence = statuses()
        // Two downloads bracket the upload: the first DownloadStarted, then upload, then the re-download
        // (the LAST DownloadStarted).
        val firstDownloadStart = sequence.indexOf("DownloadStarted")
        val uploadStarted = sequence.indexOf("UploadStarted")
        val uploadFinished = sequence.indexOf("UploadFinishSuccessfully")
        val reDownloadStart = sequence.lastIndexOf("DownloadStarted")

        assertTrue(firstDownloadStart in 0 until uploadStarted, "the first download precedes the upload")
        assertTrue(uploadStarted < uploadFinished, "UploadStarted precedes UploadFinishSuccessfully")
        assertTrue(reDownloadStart > uploadFinished, "a re-download follows the upload (not server-notified)")
        assertEquals(
            expected = 2,
            actual = sequence.count { it == "DownloadStarted" },
            "exactly two downloads bracket the single upload",
        )
    }

    // endregion
}
