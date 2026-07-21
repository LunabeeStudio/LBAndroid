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
 * Aggregates several underlying synchronization failures into one exception. Raised when more than one
 * manager (or, at the operator level, more than one group) fails during a single synchronization
 * attempt so every error can be logged or displayed instead of only the last one.
 *
 * The first underlying error is used as this exception's `cause`.
 *
 * @property errors the underlying failure causes, in the order they were collected.
 */
class LBSyncAggregateException(
    val errors: List<Throwable>,
) : Exception("Synchronization failed with ${errors.size} error(s)", errors.firstOrNull())
