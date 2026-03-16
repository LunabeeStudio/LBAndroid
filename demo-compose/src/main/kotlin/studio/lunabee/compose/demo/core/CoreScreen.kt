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

package studio.lunabee.compose.demo.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import studio.lunabee.compose.core.LbcTextSpec

private val textSpec = LbcTextSpec.AnnotatedBuilder(1) {
    append("Hello ")
    appendInlineContent("[compose]")
}

@Composable
fun CoreScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
    ) {

        Text(
            text = textSpec.annotated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            inlineContent = mapOf("[compose]" to InlineTextContent({
                {
                    "[compose]" to
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Brush.linearGradient(listOf(Color.Red, Color.Blue)))
                        }
                }
            }))
        )
    }
}