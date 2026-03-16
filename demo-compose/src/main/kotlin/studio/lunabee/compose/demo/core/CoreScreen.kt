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

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.lunabee.compose.core.LbcTextSpec

private val textSpec = LbcTextSpec.AnnotatedBuilder(1) {
    append("Hello inlined composable ")
    appendInlineContent("[compose]")
}

@Composable
fun CoreScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text("LbcTextSpec.AnnotatedBuilder")
        Text(
            text = textSpec.annotated,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            inlineContent = mapOf(
                "[compose]" to InlineTextContent(
                    placeholder = Placeholder(
                        width = 100.sp,
                        height = 16.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                    children = {
                        InlinedGradient()
                    },
                ),
            ),
        )
    }
}

@Composable
private fun InlinedGradient() {
    val transition = rememberInfiniteTransition(label = "brush")
    val animatedOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradientOffset",
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Red, Color.Blue),
                start = Offset.Zero,
                end = Offset(animatedOffset, 0f),
            ),
            cornerRadius = CornerRadius(8.sp.toPx()),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageScreenPreview() {
    Surface(Modifier.fillMaxWidth()) {
        CoreScreen()
    }
}
