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

@file:JvmName("ActivityUtils")

package studio.lunabee.extension

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat

/**
 *  Hide the keyboard if it was already shown.
 *  ## To hide the keyboard you need to have a focusable view in your layout
 *
 *  @receiver The activity where the keyboard will be hidden.
 */
fun Activity.hideSoftKeyBoard() {
    ContextCompat.getSystemService(this, InputMethodManager::class.java)?.let { imm ->
        var view = currentFocus
        // If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = (findViewById(android.R.id.content) as? ViewGroup)?.rootView
        }
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}

/**
 * Enables the edge-to-edge display for this [ComponentActivity].
 *
 * @see ComponentActivity.enableEdgeToEdge
 */
fun ComponentActivity.lbEnableEdgeToEdge(isDark: Boolean = true) {
    // Don't use SystemBarStyle.auto for navigation bar because it always adds a scrim (cf doc)
    val navigationBarStyle = if (isDark) {
        SystemBarStyle.dark(scrim = Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(
            scrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
        )
    }
    val statusBarStyle = if (isDark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(
        statusBarStyle = statusBarStyle,
        navigationBarStyle = navigationBarStyle,
    )

    // For API29(Q) or higher and 3-button navigation,
    // the following code must be written to make the navigation color completely transparent.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}
