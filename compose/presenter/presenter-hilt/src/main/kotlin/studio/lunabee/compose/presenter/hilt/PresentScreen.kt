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

package studio.lunabee.compose.presenter.hilt

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.lunabee.compose.presenter.LBPresenter

/**
 * Inject presenter as viewmodel and initialize it.
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>> PresentScreen(navScope: NavScope) {
    val presenter: Presenter = hiltViewModel()
    presenter.invoke(navScope)
}

/**
 * Build the presenter from [factory] rather than from the Hilt graph, then initialize it.
 *
 * Use this overload when the host is not a Hilt view model store owner. An input method service is the usual case: it
 * creates its own store, so [hiltViewModel] cannot resolve a presenter there and the host has to supply its own
 * [ViewModelProvider.Factory].
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>> PresentScreen(
    navScope: NavScope,
    viewModelStoreOwner: ViewModelStoreOwner,
    factory: ViewModelProvider.Factory,
) {
    val presenter: Presenter = viewModel(viewModelStoreOwner = viewModelStoreOwner, factory = factory)
    presenter.invoke(navScope)
}

/**
 * Inject presenter as viewmodel through an assisted factory and initialize it.
 *
 * Use this overload when the presenter needs a runtime value the composition has and Hilt does not, supplied through a
 * `@HiltViewModel(assistedFactory = …)` factory.
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>, reified AssistedFactory> PresentScreen(
    navScope: NavScope,
    noinline creationCallback: (AssistedFactory) -> Presenter,
) {
    val presenter: Presenter = hiltViewModel(creationCallback = creationCallback)
    presenter.invoke(navScope)
}
