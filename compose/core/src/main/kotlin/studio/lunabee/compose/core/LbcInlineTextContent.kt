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

package studio.lunabee.compose.core

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Wrapper for [LbcInlineTextContent] which allows null [placeholder] to let the consuming Text provides
 * its own measurement logic (for example to inline icon by using text height)
 *
 * @see LbcInlineTextContent
 */
@Immutable
class LbcInlineTextContent(
    /**
     * The setting object that defines the size and vertical alignment of this composable in the
     * text line. This is different from the measure of Layout
     *
     * @see Placeholder
     */
    val placeholder: Placeholder? = null,
    /**
     * The composable to be inserted into the text layout. The string parameter passed to it will
     * the alternateText given to [appendInlineContent].
     */
    val children: @Composable (String) -> Unit,
) {
    /**
     * Converts this [LbcInlineTextContent] to [InlineTextContent].
     */
    fun withPlaceHolder(
        placeholder: Placeholder = this.placeholder ?: DefaultPlaceHolder,
    ): InlineTextContent {
        return InlineTextContent(
            placeholder = placeholder,
            children = children,
        )
    }

    /**
     * Converts this [LbcInlineTextContent] to [InlineTextContent].
     */
    fun withPlaceHolderParams(
        width: TextUnit =
            this.placeholder?.width ?: DefaultPlaceHolder.width,
        height: TextUnit =
            this.placeholder?.height ?: DefaultPlaceHolder.height,
        verticalAlign: PlaceholderVerticalAlign =
            this.placeholder?.placeholderVerticalAlign ?: DefaultPlaceHolder.placeholderVerticalAlign,
    ): InlineTextContent {
        return InlineTextContent(
            placeholder = Placeholder(
                width = width,
                height = height,
                placeholderVerticalAlign = verticalAlign,
            ),
            children = children,
        )
    }

    /**
     * Converts this [LbcInlineTextContent] to [InlineTextContent].
     */
    fun withPlaceHolderParams(
        size: TextUnit,
        verticalAlign: PlaceholderVerticalAlign =
            this.placeholder?.placeholderVerticalAlign ?: DefaultPlaceHolder.placeholderVerticalAlign,
    ): InlineTextContent {
        return InlineTextContent(
            placeholder = Placeholder(
                width = size,
                height = size,
                placeholderVerticalAlign = verticalAlign,
            ),
            children = children,
        )
    }
}

fun Map<String, LbcInlineTextContent>?.toFoundation(
    block: (LbcInlineTextContent) -> InlineTextContent = { it.withPlaceHolder() },
): Map<String, InlineTextContent> {
    return this?.mapValues { (_, content) ->
        block(content)
    }.orEmpty()
}

/**
 * Arbitrary placeholder to avoid crash. Should not be used.
 */
private val DefaultPlaceHolder = Placeholder(
    width = 10.sp,
    height = 10.sp,
    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
)
