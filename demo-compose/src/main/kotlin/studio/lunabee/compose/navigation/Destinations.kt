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

package studio.lunabee.compose.navigation

object Destinations {
    const val MainRoute: String = "MainRoute"
    const val FoundationRoute: String = "FoundationRoute"
    const val HapticRoute: String = "HapticRoute"
    const val ThemeRoute: String = "ThemeRoute"
    const val CropRoute: String = "CropRoute"
    const val UiFieldRoute: String = "UiField"
    const val PresenterRoute: String = "Presenter"
    const val GlanceRoute: String = "GlanceRoute"
    const val ImageRoute: String = "ImageRoute"
    const val KtorRoute: String = "KtorRoute"
    const val CoreRoute: String = "CoreRoute"
    const val LbcNavigation: String = "LbcNavigation"

    val BackNavigationScreen: List<String> = listOf(
        FoundationRoute,
        ThemeRoute,
        HapticRoute,
        CropRoute,
        UiFieldRoute,
        PresenterRoute,
        GlanceRoute,
        ImageRoute,
        KtorRoute,
        CoreRoute,
        LbcNavigation,
    )
}
