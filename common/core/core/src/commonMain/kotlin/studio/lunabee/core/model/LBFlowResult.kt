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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlin.experimental.ExperimentalTypeInference

/**
 * Generic result wrapper to wrap data fetched asynchronously with a status.
 *
 * @param T Type of the wrapped data. Might be [Unit] if the expected result does not contain any data.
 */
sealed class LBFlowResult<out T> {

    /**
     * Loading implementation of [LBFlowResult]
     *
     * @param T @inheritDoc
     * @property partialData The data already fetched or null if non applicable
     * @property progress The current progress
     */
    data class Loading<out T>(val partialData: T? = null, val progress: Float? = null) : LBFlowResult<T>()

    /**
     * Success implementation of [LBFlowResult]
     *
     * @param T @inheritDoc
     * @property successData The final non-null data
     */
    data class Success<out T>(val successData: T) : LBFlowResult<T>()

    /**
     * Success implementation of [LBFlowResult]
     *
     * @param T @inheritDoc
     * @property throwable The throwable that caused the failure or null if non applicable
     * @property failureData The final data or null if non applicable
     */
    data class Failure<out T>(val throwable: Throwable? = null, val failureData: T? = null) : LBFlowResult<T>() {
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
                is Loading -> partialData
                is Success -> successData
                is Failure -> failureData
            }
        }

    /**
     * Cast [LBFlowResult] to [LBResult] for [Success] and [Failure] results.
     * Throw [IllegalStateException] when called with a [Loading] result.
     */
    @Throws(IllegalStateException::class)
    fun asResult(): LBResult<T> = when (this) {
        is Loading -> error("Loading cannot be cast to LBResult")
        is Success -> LBResult.Success(successData)
        is Failure -> LBResult.Failure(throwable, failureData)
    }

    companion object {
        /**
         * Applies transform functions to each result of the given flow.
         * By default, failures preserve the throwable if present and loadings preserve the progress.
         *
         * @see [Flow.transform]
         */
        @OptIn(ExperimentalTypeInference::class)
        inline fun <T, R> Flow<LBFlowResult<T>>.transformResult(
            crossinline transformError:
            suspend FlowCollector<LBFlowResult<R>>.(value: Failure<T>) -> Unit =
                FlowCollector<LBFlowResult<R>>::transformError,
            crossinline transformLoading:
            suspend FlowCollector<LBFlowResult<R>>.(value: Loading<T>) -> Unit =
                FlowCollector<LBFlowResult<R>>::transformLoading,
            crossinline transform: suspend FlowCollector<LBFlowResult<R>>.(value: Success<T>) -> Unit =
                FlowCollector<LBFlowResult<R>>::transformSuccess,
        ): Flow<LBFlowResult<R>> = flow {
            collect { result ->
                return@collect when (result) {
                    is Failure -> transformError(result)
                    is Loading -> transformLoading(result)
                    is Success -> transform(result)
                }
            }
        }

        /**
         * Returns a flow containing the results of applying the given [transform] function to each result of the original flow.
         * By default, failures preserve the throwable if present and loadings preserve the progress.
         *
         * @see [Flow.map]
         */
        inline fun <T, R> Flow<LBFlowResult<T>>.mapResult(
            crossinline mapError: suspend (error: Throwable?) -> Throwable? = ::mapError,
            crossinline mapLoading: suspend (progress: Float?) -> Float? = ::mapLoading,
            crossinline mapSuccess: suspend (value: T) -> R = ::mapSuccess,
        ): Flow<LBFlowResult<R>> = transformResult(
            transformError = { value ->
                return@transformResult emit(Failure(mapError(value.throwable), value.failureData?.let { mapSuccess(it) }))
            },
            transformLoading = { value ->
                return@transformResult emit(Loading(value.partialData?.let { mapSuccess(it) }, mapLoading(value.progress)))
            },
            transform = { value ->
                return@transformResult emit(Success(mapSuccess(value.successData)))
            },
        )

        fun <T> Flow<LBFlowResult<T>>.unit(): Flow<LBFlowResult<Unit>> = mapResult { /* Unit */ }
    }
}

// FIXME workaround use function ref instead of lambda for default params
//  https://youtrack.jetbrains.com/issue/KT-60506/IllegalArgumentException-suspend-default-lambda-cannot-be-inlined-caused-by-the-combination-of-suspend-functions-inlining-and
//  https://youtrack.jetbrains.com/issue/KT-45505/IAE-suspend-default-lambda-X-cannot-be-inlined-use-a-function-reference-instead-with-crossinline-suspend-lambda-and-default

@Suppress("RedundantSuspendModifier", "UNCHECKED_CAST")
@PublishedApi
internal suspend fun <T, R> mapSuccess(data: T): R = data as R

@Suppress("RedundantSuspendModifier")
@PublishedApi
internal suspend fun mapError(throwable: Throwable?): Throwable? = throwable

@Suppress("RedundantSuspendModifier")
@PublishedApi
internal suspend fun mapLoading(progress: Float?): Float? = progress

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal suspend fun <T, R> FlowCollector<LBFlowResult<R>>.transformSuccess(value: LBFlowResult.Success<T>) {
    emit(value as LBFlowResult<R>)
}

@PublishedApi
internal suspend fun <T, R> FlowCollector<LBFlowResult<R>>.transformError(value: LBFlowResult.Failure<T>) {
    emit(LBFlowResult.Failure(throwable = value.throwable))
}

@PublishedApi
internal suspend fun <T, R> FlowCollector<LBFlowResult<R>>.transformLoading(value: LBFlowResult.Loading<T>) {
    emit(LBFlowResult.Loading(progress = value.progress))
}
