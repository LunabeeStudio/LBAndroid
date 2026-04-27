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

package studio.lunabee.compose.demo.core.topbar.action

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import studio.lunabee.compose.R
import studio.lunabee.compose.core.LbcImageSpec
import studio.lunabee.compose.core.LbcTextSpec

sealed interface CoreTopBarAction {
    data class Icon(
        val image: LbcImageSpec.Icon,
        val tint: @Composable () -> Color = { MaterialTheme.colorScheme.tertiary },
        val onClick: () -> Unit,
        val contentDescription: LbcTextSpec?,
    ) : CoreTopBarAction

    data class Custom(
        val content: @Composable () -> Unit,
    ) : CoreTopBarAction

    companion object {
        fun backAction(
            onClick: () -> Unit,
            tint: @Composable () -> Color = { Color.Unspecified },
        ) = Icon(
            image = LbcImageSpec.Icon(R.drawable.ic_back, tint = tint),
            onClick = onClick,
            contentDescription = null,
        )

        fun closeAction(
            onClick: () -> Unit,
        ) = Icon(
            image = LbcImageSpec.Icon(R.drawable.ic_close),
            onClick = onClick,
            contentDescription = null,
        )
    }
}
