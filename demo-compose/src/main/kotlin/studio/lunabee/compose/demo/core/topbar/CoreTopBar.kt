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

package studio.lunabee.compose.demo.core.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import studio.lunabee.compose.demo.core.topbar.action.CoreTopBarActionCell
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.demo.core.topbar.action.CoreTopBarAction

val CoreTopBarHeight: Dp = 52.dp

@Composable
fun CoreTopBar(
    title: LbcTextSpec?,
    modifier: Modifier = Modifier,
    leadingAction: CoreTopBarAction? = null,
    trailingAction: CoreTopBarAction? = null,
) {
    Row(
        modifier = modifier
            .heightIn(min = CoreTopBarHeight)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        leadingAction?.let {
            CoreTopBarActionCell(coreTopBarAction = it)
        } ?: Spacer(modifier = Modifier.width(CoreTopBarConstants.TopBarActionSize))
        title?.let {
            Text(
                text = it.string,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } ?: Spacer(modifier = Modifier.weight(1f))
        trailingAction?.let {
            CoreTopBarActionCell(coreTopBarAction = it)
        } ?: Spacer(modifier = Modifier.width(CoreTopBarConstants.TopBarActionSize))
    }
}
