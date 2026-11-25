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

package studio.lunabee.monitoring.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
internal actual fun rememberTextSharer(): (text: String) -> Unit {
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        { text ->
            var presenter: UIViewController = viewController
            while (true) presenter = presenter.presentedViewController ?: break
            val activityController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // Required on iPad, where the share sheet is a popover.
            activityController.popoverPresentationController?.sourceView = presenter.view
            presenter.presentViewController(activityController, animated = true, completion = null)
        }
    }
}
