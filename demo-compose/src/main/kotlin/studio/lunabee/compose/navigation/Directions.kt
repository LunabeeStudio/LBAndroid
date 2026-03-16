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

import androidx.navigation.NavHostController

typealias ToDirection = () -> Unit

class Directions(navController: NavHostController) {
    val navigateToFoundation: ToDirection = {
        navController.navigate(route = Destinations.FoundationRoute) {
            popUpTo(route = Destinations.MainRoute) { inclusive = false }
        }
    }

    val navigateToHaptic: ToDirection = {
        navController.navigate(route = Destinations.HapticRoute) {
            popUpTo(route = Destinations.MainRoute) { inclusive = false }
        }
    }

    val navigateToTheme: ToDirection = {
        navController.navigate(route = Destinations.ThemeRoute) {
            popUpTo(route = Destinations.ThemeRoute) { inclusive = false }
        }
    }

    val navigateToCrop: ToDirection = {
        navController.navigate(route = Destinations.CropRoute) {
            popUpTo(route = Destinations.MainRoute) { inclusive = false }
        }
    }

    val navigateToPreviousScreen: ToDirection = {
        navController.popBackStack()
    }

    val navigateToUiFields: ToDirection = {
        navController.navigate(route = Destinations.UiFieldRoute)
    }

    val navigateToPresenterScreen: ToDirection = {
        navController.navigate(route = Destinations.PresenterRoute)
    }

    val navigateToGlanceScreen: ToDirection = {
        navController.navigate(route = Destinations.GlanceRoute)
    }

    val navigateToImageScreen: ToDirection = {
        navController.navigate(route = Destinations.ImageRoute)
    }

    val navigateToKtorScreen: ToDirection = {
        navController.navigate(route = Destinations.KtorRoute)
    }

    val navigateToCoreScreen: ToDirection = {
        navController.navigate(route = Destinations.CoreRoute)
    }
}
