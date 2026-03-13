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

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Stable
sealed interface LbcImageSpec {

    @Stable
    class Icon(
        @DrawableRes val drawableRes: Int,
        val tint: @Composable () -> Color = { Color.Unspecified },
    ) : LbcImageSpec

    @Stable
    class KtImageVector(
        val icon: ImageVector,
        val tint: @Composable () -> Color = { Color.Unspecified },
    ) : LbcImageSpec

    @Stable
    class ImageDrawable(
        @DrawableRes val drawableRes: Int,
        val uiMode: Int = Configuration.UI_MODE_TYPE_UNDEFINED,
    ) : LbcImageSpec

    @Stable
    class Bitmap(
        val bitmap: android.graphics.Bitmap,
    ) : LbcImageSpec

    @Stable
    class ByteArray(
        val byteArray: kotlin.ByteArray,
    ) : LbcImageSpec

    @Stable
    class Url(
        val url: String,
        val allowCaching: Boolean = true,
        val fallback: LbcImageSpec? = null,
    ) : LbcImageSpec

    @Stable
    class Uri(
        val uri: android.net.Uri,
        val allowCaching: Boolean = true,
        val fallback: LbcImageSpec? = null,
    ) : LbcImageSpec
}
