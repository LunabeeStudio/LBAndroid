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

package studio.lunabee.compose.demo.presenter.presenternav

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import studio.lunabee.compose.common.MenuDescription
import studio.lunabee.compose.common.MenuEntry
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.demo.presenter.multistate.MultiStateDestination
import studio.lunabee.compose.demo.presenter.pullToRefresh.PullToRefreshDestination
import studio.lunabee.compose.demo.presenter.simple.SimpleExampleDestination
import studio.lunabee.compose.demo.presenter.timer.TimerHiltDestination
import studio.lunabee.compose.demo.presenter.timer.TimerKoinDestination

@Composable
fun PresenterDemoList(
    navigate: (String) -> Unit,
) {
    val menus = listOf(
        MenuDescription(
            title = LbcTextSpec.Raw("Common stuff"),
            subtitle = null,
            direction = { navigate(SimpleExampleDestination.route) },
        ),
        MenuDescription(
            title = LbcTextSpec.Raw("Timer hilt"),
            subtitle = null,
            direction = { navigate(TimerHiltDestination.route) },
        ),
        MenuDescription(
            title = LbcTextSpec.Raw("Timer koin"),
            subtitle = null,
            direction = { navigate(TimerKoinDestination.route) },
        ),
        MenuDescription(
            title = LbcTextSpec.Raw("Pull to refresh"),
            subtitle = null,
            direction = { navigate(PullToRefreshDestination.route) },
        ),
        MenuDescription(
            title = LbcTextSpec.Raw("Multi state"),
            subtitle = null,
            direction = { navigate(MultiStateDestination.Route) },
        ),
    )

    LazyColumn {
        items(
            items = menus,
            key = { it.title.hashCode() },
        ) { menu ->
            MenuEntry(menu = menu)
            HorizontalDivider()
        }
    }
}
