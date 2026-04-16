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

package studio.lunabee.core.model

/**
 * Generic result wrapper to wrap data with a status.
 *
 * @param T Type of the wrapped data. Might be [Unit] if the expected result does not contain any data.
 */
sealed class LBResult<out T> {

    /**
     * Success implementation of [LBResult]
     *
     * @param T @inheritDoc
     * @property successData The final non-null data
     */
    data class Success<out T>(val successData: T) : LBResult<T>()

    /**
     * Success implementation of [LBResult]
     *
     * @param T @inheritDoc
     * @property throwable The throwable that caused the failure or null if non applicable
     * @property failureData The final data or null if non applicable
     */
    data class Failure<out T>(val throwable: Throwable? = null, val failureData: T? = null) : LBResult<T>() {
        /**
         * Secondary constructor to instantiate a failure result from an error string
         */
        @Suppress("unused")
        constructor(message: String) : this(throwable = Exception(message))
    }

    /**
     * Common getter for the data of any result states.
     */
    val data: T?
        get() {
            return when (this) {
                is Success -> successData
                is Failure -> failureData
            }
        }

    /**
     * @return the success data or thrown the failure throwable.
     */
    @Throws(Exception::class)
    fun getOrThrow(defaultMessage: String? = null): T = when (this) {
        is Failure -> throw this.throwable ?: Exception(defaultMessage ?: "Failed without exception")
        is Success -> this.successData
    }

    /**
     * Cast [LBResult] to [LBFlowResult]
     */
    fun asFlowResult(): LBFlowResult<T> = when (this) {
        is Success -> LBFlowResult.Success(successData)
        is Failure -> LBFlowResult.Failure(throwable, failureData)
    }
}

/**
 * Returns the first failure
 */
fun <T> Collection<LBResult<T>>.firstFailure(): LBResult.Failure<T>? {
    return this.filterIsInstance<LBResult.Failure<T>>().firstOrNull()
}
