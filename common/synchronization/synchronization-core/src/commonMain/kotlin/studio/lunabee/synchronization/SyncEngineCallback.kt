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

/**
 * Marks an SPI member the engine calls from **inside** a sync run, while [LBSyncOperator] holds its sync
 * lock. An override of such a member must never request a sync from the operator: the lock is not
 * reentrant, so the request is refused at runtime with [LBSyncReentrantCallException], and the
 * `SyncOperatorReentrantCall` lint rule (bundled with the storage backends) flags the call site.
 *
 * Members reached outside a run — `startServerNotificationListener`, `hasSomethingToUpload`, a LiveQuery
 * hook — are deliberately NOT marked: a sync request from there is legitimate.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SyncEngineCallback
