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

package studio.lunabee.compose.demo.navigation

import androidx.compose.runtime.Composable
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import studio.lunabee.compose.demo.navigation.presenter.TestPresenterNavScope
import studio.lunabee.compose.demo.navigation.presenter.TestPresenterPresenter
import studio.lunabee.compose.navigation.CoreDestination
import studio.lunabee.compose.navigation.NavigationHelper
import studio.lunabee.compose.presenter.LBPresenter

@Serializable
data class ScreenA(
    val title: String = "Screen A",
) : CoreDestination<TestPresenterNavScope>() {

    @Composable
    override fun getPresenter(): TestPresenterPresenter {
        return koinViewModel<TestPresenterPresenter>(
            parameters = { parametersOf(title) },
        )
    }

    override fun getNavScope(navigationHelper: NavigationHelper): TestPresenterNavScope {
        return object : TestPresenterNavScope {
            override val navigate: () -> Unit = {
                navigationHelper.navigate(ScreenB())
            }
        }
    }
}

@Serializable
data class ScreenB(
    val title: String = "Screen B",
) : CoreDestination<TestPresenterNavScope>() {

    @Composable
    override fun getPresenter(): LBPresenter<*, TestPresenterNavScope, *> {
        return koinViewModel<TestPresenterPresenter>(
            parameters = { parametersOf(title) },
        )
    }

    override fun getNavScope(navigationHelper: NavigationHelper): TestPresenterNavScope {
        return object : TestPresenterNavScope {
            override val navigate: () -> Unit = {
                navigationHelper.modal(BottomSheet())
            }
        }
    }
}

@Serializable
data class BottomSheet(
    val title: String = "Bottom Sheet",
) : CoreDestination<TestPresenterNavScope>() {

    @Composable
    override fun getPresenter(): LBPresenter<*, TestPresenterNavScope, *> {
        return koinViewModel<TestPresenterPresenter>(
            parameters = { parametersOf(title) },
        )
    }

    override fun getNavScope(navigationHelper: NavigationHelper): TestPresenterNavScope {
        return object : TestPresenterNavScope {
            override val navigate: () -> Unit = {
                navigationHelper.navigate(BottomSheet2())
            }
        }
    }
}

@Serializable
data class BottomSheet2(
    val title: String = "Bottom Sheet B",
) : CoreDestination<TestPresenterNavScope>() {

    @Composable
    override fun getPresenter(): LBPresenter<*, TestPresenterNavScope, *> {
        return koinViewModel<TestPresenterPresenter>(
            parameters = { parametersOf(title) },
        )
    }

    override fun getNavScope(navigationHelper: NavigationHelper): TestPresenterNavScope {
        return object : TestPresenterNavScope {
            override val navigate: () -> Unit = {
                navigationHelper.modal(BottomSheet2())
            }
        }
    }
}
