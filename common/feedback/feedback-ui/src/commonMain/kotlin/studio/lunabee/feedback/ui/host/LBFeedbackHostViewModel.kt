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

package studio.lunabee.feedback.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class LBFeedbackHostViewModel(start: LBFeedbackRoute) : ViewModel() {
    class Entry(val route: LBFeedbackRoute) : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private val _stack = MutableStateFlow(listOf(Entry(start)))
    val stack: StateFlow<List<Entry>> = _stack.asStateFlow()

    fun push(route: LBFeedbackRoute) = _stack.update { it + Entry(route) }

    fun pop(): Boolean {
        val stack = _stack.value
        if (stack.size < 2) return false
        _stack.value = stack.dropLast(1)
        stack.last().viewModelStore.clear()
        return true
    }

    override fun onCleared() {
        _stack.value.forEach { it.viewModelStore.clear() }
    }
}
