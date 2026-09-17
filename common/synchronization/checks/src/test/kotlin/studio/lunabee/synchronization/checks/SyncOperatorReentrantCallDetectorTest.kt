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

package studio.lunabee.synchronization.checks

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class SyncOperatorReentrantCallDetectorTest {

    @Test
    fun a_request_from_an_annotated_callback_is_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator
            import studio.lunabee.synchronization.syncmanager.LBSyncManager

            class MyManager : LBSyncManager() {
                override suspend fun fetchRequest() {
                    LBSyncOperator.sync(this)
                }
            }
            """,
        ).expectErrorCount(1)
    }

    @Test
    fun a_full_run_requested_from_an_annotated_callback_is_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator
            import studio.lunabee.synchronization.syncmanager.LBSyncManager

            class MyManager : LBSyncManager() {
                override suspend fun fetchRequest() {
                    LBSyncOperator.syncAllManagers()
                    LBSyncOperator.syncGroup("other")
                }
            }
            """,
        ).expectErrorCount(2)
    }

    @Test
    fun a_request_nested_deeper_in_a_callback_is_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator
            import studio.lunabee.synchronization.syncmanager.LBSyncManager

            class MyManager : LBSyncManager() {
                override suspend fun fetchRequest() {
                    if (true) {
                        repeat(2) {
                            LBSyncOperator.sync(this)
                        }
                    }
                }
            }
            """,
        ).expectErrorCount(1)
    }

    @Test
    fun a_request_launched_from_a_callback_is_not_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator
            import studio.lunabee.synchronization.syncmanager.LBSyncManager

            fun launch(block: suspend () -> Unit) = Unit

            class MyManager : LBSyncManager() {
                override suspend fun fetchRequest() {
                    launch { LBSyncOperator.sync(this@MyManager) }
                }
            }
            """,
        ).expectClean()
    }

    @Test
    fun a_request_from_a_member_that_is_not_a_callback_is_not_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator
            import studio.lunabee.synchronization.syncmanager.LBSyncManager

            class MyManager : LBSyncManager() {
                override suspend fun fetchRequest() = Unit

                override suspend fun startServerNotificationListener(): Boolean {
                    LBSyncOperator.sync(this)
                    return true
                }
            }
            """,
        ).expectClean()
    }

    @Test
    fun a_request_made_outside_a_sync_manager_is_not_reported() {
        runOnSource(
            """
            package test

            import studio.lunabee.synchronization.LBSyncOperator

            class MyViewModel {
                suspend fun refresh() {
                    LBSyncOperator.syncAllManagers()
                }
            }
            """,
        ).expectClean()
    }

    private fun runOnSource(source: String): TestLintResult = lint()
        .files(annotationStub, operatorStub, managerStub, kotlin(source).indented())
        .issues(SyncOperatorReentrantCallDetector.issue)
        .allowMissingSdk()
        .run()

    private val annotationStub: TestFile = kotlin(
        """
        package studio.lunabee.synchronization

        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
        @Retention(AnnotationRetention.BINARY)
        annotation class SyncEngineCallback
        """,
    ).indented()

    private val operatorStub: TestFile = kotlin(
        """
        package studio.lunabee.synchronization

        import studio.lunabee.synchronization.syncmanager.LBSyncManager

        object LBSyncOperator {
            suspend fun sync(manager: LBSyncManager) = Unit
            suspend fun syncAllManagers() = Unit
            suspend fun syncGroup(name: String) = Unit
        }
        """,
    ).indented()

    private val managerStub: TestFile = kotlin(
        """
        package studio.lunabee.synchronization.syncmanager

        import studio.lunabee.synchronization.SyncEngineCallback

        abstract class LBSyncManager {
            @SyncEngineCallback
            protected abstract suspend fun fetchRequest()

            open suspend fun startServerNotificationListener(): Boolean = true
        }
        """,
    ).indented()
}
