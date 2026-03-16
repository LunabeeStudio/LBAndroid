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

package studio.lunabee.compose

import androidx.compose.runtime.Composable
import studio.lunabee.compose.common.MenuDescription
import studio.lunabee.compose.common.MenuSection
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.navigation.Directions

@Composable
fun MainScreen(
    directions: Directions,
) {
    MenuSection(
        menus = listOf(
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.foundation_screen_title),
                subtitle = LbcTextSpec.StringResource(R.string.foundation_screen_subtitle),
                direction = directions.navigateToFoundation,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.haptic_screen_title),
                subtitle = LbcTextSpec.StringResource(R.string.haptic_screen_subtitle),
                direction = directions.navigateToHaptic,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.theme_screen_title),
                subtitle = LbcTextSpec.StringResource(R.string.theme_screen_subtitle),
                direction = directions.navigateToTheme,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.crop_screen_title),
                subtitle = LbcTextSpec.StringResource(R.string.crop_screen_subtitle),
                direction = directions.navigateToCrop,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.uiFields_screen_title),
                subtitle = LbcTextSpec.StringResource(R.string.uiFields_screen_subtitle),
                direction = directions.navigateToUiFields,
            ),
            MenuDescription(
                title = LbcTextSpec.Raw("Presenter"),
                subtitle = LbcTextSpec.Raw("Composable architecture with presenter"),
                direction = directions.navigateToPresenterScreen,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.glance_title),
                subtitle = LbcTextSpec.StringResource(R.string.glance_description),
                direction = directions.navigateToGlanceScreen,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.image_title),
                subtitle = LbcTextSpec.StringResource(R.string.image_description),
                direction = directions.navigateToImageScreen,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.ktor_title),
                subtitle = LbcTextSpec.StringResource(R.string.ktor_description),
                direction = directions.navigateToKtorScreen,
            ),
            MenuDescription(
                title = LbcTextSpec.StringResource(R.string.ktor_title),
                subtitle = LbcTextSpec.StringResource(R.string.ktor_description),
                direction = directions.navigateToKtorScreen,
            ),
        ),
    )
}
