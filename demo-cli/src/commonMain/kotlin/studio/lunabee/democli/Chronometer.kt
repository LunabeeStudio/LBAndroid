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

package studio.lunabee.democli

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import studio.lunabee.core.model.LBFlowResult
import kotlin.time.Duration
import kotlin.time.TimeSource

fun chronometer(
    total: Duration,
    tick: Duration,
): Flow<LBFlowResult<Duration>> = flow {
    if (!total.isPositive() || !tick.isPositive() || tick > total) {
        emit(
            LBFlowResult.Failure(
                throwable = IllegalArgumentException(
                    "total and tick must be positive and tick must be <= total (total=$total, tick=$tick)",
                ),
            ),
        )
        return@flow
    }
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        delay(tick)
        val elapsed = start.elapsedNow()
        if (elapsed >= total) {
            emit(LBFlowResult.Success(total))
            return@flow
        }
        emit(
            LBFlowResult.Loading(
                partialData = elapsed,
                progress = (elapsed / total).toFloat(),
            ),
        )
    }
}
