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

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import studio.lunabee.compose.common.AppDemoTheme
import studio.lunabee.compose.demo.monitoring.LBMonitoringDemo
import studio.lunabee.compose.navigation.Destinations
import studio.lunabee.compose.navigation.Directions
import studio.lunabee.compose.navigation.MainNavGraph

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LBMonitoringDemo.init(context = this.applicationContext)

        setContent {
            val navController = rememberNavController()
            val directions = remember { Directions(navController = navController) }
            var title: String by rememberSaveable {
                mutableStateOf(value = getString(R.string.application_name))
            }
            var shouldShowBackNav: Boolean by rememberSaveable {
                mutableStateOf(value = false)
            }

            LaunchedEffect(navController) {
                navController.addOnDestinationChangedListener { _, destination, _ ->
                    title = when (destination.route) {
                        Destinations.MainRoute -> getString(R.string.application_name)
                        Destinations.ThemeRoute -> getString(R.string.theme_screen_title)
                        Destinations.HapticRoute -> getString(R.string.haptic_screen_title)
                        Destinations.FoundationRoute -> getString(R.string.foundation_screen_title)
                        else -> destination.route.orEmpty()
                    }

                    shouldShowBackNav = Destinations.BackNavigationScreen.contains(destination.route)
                }
            }

            AppDemoTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = title) },
                            navigationIcon = if (shouldShowBackNav) {
                                {
                                    IconButton(onClick = directions.navigateToPreviousScreen) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_back),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            } else {
                                { }
                            },
                        )
                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues = paddingValues),
                    ) {
                        MainNavGraph(
                            navController = navController,
                            directions = directions,
                        )
                    }
                }
            }
        }
    }

    private fun enableEdgeToEdge() {
        // For status bar color with elevation.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Don't use SystemBarStyle.auto for navigation bar because it always add a scrim (cf doc)
        val navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(scrim = Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT)
        }
        val statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
    }
}
