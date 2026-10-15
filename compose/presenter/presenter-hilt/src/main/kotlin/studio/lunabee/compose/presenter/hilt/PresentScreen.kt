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
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.lunabee.compose.presenter.LBPresenter

/**
 * Inject presenter as viewmodel and initialize it.
 *
 * Renders nothing once the [LocalLifecycleOwner] is destroyed, see [isLifecycleOwnerDestroyed].
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>> PresentScreen(navScope: NavScope) {
    if (isLifecycleOwnerDestroyed()) return
    val presenter: Presenter = hiltViewModel()
    presenter.invoke(navScope)
}

/**
 * Build the presenter from [factory] rather than from the Hilt graph, then initialize it.
 *
 * Use this overload when the host is not a Hilt view model store owner. An input method service is the usual case: it
 * creates its own store, so [hiltViewModel] cannot resolve a presenter there and the host has to supply its own
 * [ViewModelProvider.Factory].
 *
 * Such a host has no default [CreationExtras], so a presenter reading its route arguments from a `SavedStateHandle`
 * needs them supplied through [extras].
 *
 * Renders nothing once the [LocalLifecycleOwner] is destroyed, see [isLifecycleOwnerDestroyed].
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>> PresentScreen(
    navScope: NavScope,
    viewModelStoreOwner: ViewModelStoreOwner,
    factory: ViewModelProvider.Factory,
    extras: CreationExtras = if (viewModelStoreOwner is HasDefaultViewModelProviderFactory) {
        viewModelStoreOwner.defaultViewModelCreationExtras
    } else {
        CreationExtras.Empty
    },
) {
    if (isLifecycleOwnerDestroyed()) return
    val presenter: Presenter = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = factory,
        extras = extras,
    )
    presenter.invoke(navScope)
}

/**
 * Inject presenter as viewmodel through an assisted factory and initialize it.
 *
 * Use this overload when the presenter needs a runtime value the composition has and Hilt does not, supplied through a
 * `@HiltViewModel(assistedFactory = …)` factory.
 *
 * Renders nothing once the [LocalLifecycleOwner] is destroyed, see [isLifecycleOwnerDestroyed].
 */
@Composable
inline fun <NavScope : Any, reified Presenter : LBPresenter<*, NavScope, *>, reified AssistedFactory> PresentScreen(
    navScope: NavScope,
    noinline creationCallback: (AssistedFactory) -> Presenter,
) {
    if (isLifecycleOwnerDestroyed()) return
    val presenter: Presenter = hiltViewModel(creationCallback = creationCallback)
    presenter.invoke(navScope)
}

/**
 * Whether the [LocalLifecycleOwner] of the composition has reached [Lifecycle.State.DESTROYED].
 *
 * Inside a navigation-compose `NavHost`, the lifecycle owner is the screen's `NavBackStackEntry`. The `NavHost` can
 * still recompose an entry that has just been destroyed while it leaves the composition, and reading the view models of
 * a destroyed entry throws `IllegalStateException: You cannot access the NavBackStackEntry's ViewModels after the
 * NavBackStackEntry is destroyed.` A destroyed entry never becomes active again, so the screen renders nothing instead.
 */
@PublishedApi
@Composable
internal fun isLifecycleOwnerDestroyed(): Boolean {
    val lifecycleState: Lifecycle.State by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return lifecycleState == Lifecycle.State.DESTROYED
}
