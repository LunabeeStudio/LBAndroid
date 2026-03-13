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

package studio.lunabee.compose.image

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import studio.lunabee.compose.core.LbcImageSpec
import studio.lunabee.compose.core.LbcTextSpec

@Composable
fun LbcImage(
    imageSpec: LbcImageSpec,
    modifier: Modifier = Modifier,
    contentDescription: LbcTextSpec? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
    errorPainter: Painter? = null,
) {
    when (imageSpec) {
        is LbcImageSpec.Bitmap -> {
            Image(
                bitmap = imageSpec.bitmap.asImageBitmap(),
                contentDescription = contentDescription?.string,
                modifier = modifier,
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
            )
        }

        is LbcImageSpec.ImageDrawable -> DrawableImage(
            imageSpec = imageSpec,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            onState = onState,
            errorPainter = errorPainter,
        )

        is LbcImageSpec.Icon -> {
            val tint = imageSpec.tint.invoke().takeIf { it != Color.Unspecified } ?: LocalContentColor.current
            Icon(
                painter = painterResource(id = imageSpec.drawableRes),
                contentDescription = contentDescription?.string,
                modifier = modifier,
                tint = tint,
            )
        }

        is LbcImageSpec.KtImageVector -> {
            val tint = imageSpec.tint.invoke().takeIf { it != Color.Unspecified } ?: LocalContentColor.current
            Icon(
                imageVector = imageSpec.icon,
                contentDescription = contentDescription?.string,
                modifier = modifier,
                tint = tint,
            )
        }

        is LbcImageSpec.Url -> UrlImage(
            imageSpec = imageSpec,
            modifier = modifier,
            contentDescription = contentDescription,
            onState = onState,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            errorPainter = errorPainter,
        )

        is LbcImageSpec.ByteArray ->
            AsyncImage(
                model = imageSpec.byteArray,
                contentDescription = contentDescription?.string,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                error = errorPainter,
                onError = onState,
                onLoading = onState,
                onSuccess = onState,
                colorFilter = colorFilter,
            )

        is LbcImageSpec.Uri -> UriImage(
            imageSpec = imageSpec,
            modifier = modifier,
            contentDescription = contentDescription,
            onState = onState,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            errorPainter = errorPainter,
        )
    }
}

@Composable
private fun UrlImage(
    imageSpec: LbcImageSpec.Url,
    modifier: Modifier,
    contentDescription: LbcTextSpec?,
    onState: ((AsyncImagePainter.State) -> Unit)?,
    contentScale: ContentScale,
    alignment: Alignment,
    colorFilter: ColorFilter?,
    errorPainter: Painter?,
) {
    var showFallback by remember(imageSpec.url) { mutableStateOf(false) }
    if (showFallback) {
        imageSpec.fallback?.let { fallback ->
            LbcImage(
                imageSpec = fallback,
                modifier = modifier,
                contentDescription = contentDescription,
                onState = onState,
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
                errorPainter = errorPainter,
            )
        }
    } else {
        val builder = ImageRequest
            .Builder(LocalContext.current)
            .data(imageSpec.url)
            .decoderFactory(SvgDecoder.Factory())
        if (!imageSpec.allowCaching) {
            builder.diskCachePolicy(CachePolicy.DISABLED)
            builder.memoryCachePolicy(CachePolicy.DISABLED)
        }
        AsyncImage(
            model = builder.build(),
            contentDescription = contentDescription?.string,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            error = errorPainter,
            onError = {
                @Suppress("AssignedValueIsNeverRead")
                showFallback = imageSpec.fallback != null
                onState?.invoke(it)
            },
            onLoading = onState,
            onSuccess = onState,
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun UriImage(
    imageSpec: LbcImageSpec.Uri,
    modifier: Modifier,
    contentDescription: LbcTextSpec?,
    onState: ((AsyncImagePainter.State) -> Unit)?,
    contentScale: ContentScale,
    alignment: Alignment,
    colorFilter: ColorFilter?,
    errorPainter: Painter?,
) {
    var showFallback by remember(imageSpec.uri) { mutableStateOf(false) }
    if (showFallback) {
        imageSpec.fallback?.let { fallback ->
            LbcImage(
                imageSpec = fallback,
                modifier = modifier,
                contentDescription = contentDescription,
                onState = onState,
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
                errorPainter = errorPainter,
            )
        }
    } else {
        val builder = ImageRequest
            .Builder(LocalContext.current)
            .data(imageSpec.uri)
        if (!imageSpec.allowCaching) {
            builder.diskCachePolicy(CachePolicy.DISABLED)
            builder.memoryCachePolicy(CachePolicy.DISABLED)
        }
        AsyncImage(
            model = builder.build(),
            contentDescription = contentDescription?.string,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            error = errorPainter,
            onError = {
                @Suppress("AssignedValueIsNeverRead")
                showFallback = imageSpec.fallback != null
                onState?.invoke(it)
            },
            onLoading = onState,
            onSuccess = onState,
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun DrawableImage(
    imageSpec: LbcImageSpec.ImageDrawable,
    contentDescription: LbcTextSpec?,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment,
    colorFilter: ColorFilter?,
    onState: ((AsyncImagePainter.State) -> Unit)?,
    errorPainter: Painter?,
) {
    if (imageSpec.uiMode == Configuration.UI_MODE_TYPE_UNDEFINED) {
        Image(
            painter = painterResource(id = imageSpec.drawableRes),
            contentDescription = contentDescription?.string,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
        )
    } else {
        val configuration = Configuration().apply {
            uiMode = imageSpec.uiMode
        }
        val resources = LocalContext.current.createConfigurationContext(configuration).resources
        val bitmap = ResourcesCompat.getDrawable(resources, imageSpec.drawableRes, null)!!.toBitmap()
        LbcImage(
            imageSpec = LbcImageSpec.Bitmap(bitmap),
            modifier = modifier,
            contentDescription = contentDescription,
            onState = onState,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            errorPainter = errorPainter,
        )
    }
}
