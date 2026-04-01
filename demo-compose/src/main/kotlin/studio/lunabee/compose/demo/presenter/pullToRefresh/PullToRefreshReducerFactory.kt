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

package studio.lunabee.compose.demo.presenter.pullToRefresh

import studio.lunabee.compose.presenter.LBPresenterContext
import studio.lunabee.compose.presenter.LBSingleReducerFactory
import javax.inject.Inject

class PullToRefreshReducerFactory @Inject constructor() :
    LBSingleReducerFactory<PullToRefreshUiState, PullToRefreshNavScope, PullToRefreshAction> {
    /**
     * Creates the reducer instance using the context owned by the presenter.
     */
    override fun create(context: LBPresenterContext<PullToRefreshAction>): PullToRefreshReducer = PullToRefreshReducer(
        coroutineScope = context.coroutineScope,
        emitUserAction = context.emitUserAction,
    )
}
