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

package studio.lunabee.feedback.ui.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import studio.lunabee.feedback.core.model.LBFeedbackAttachment

internal const val ScreenshotMaxBytes: Int = 4_500_000
internal const val ScreenshotMimeType: String = "image/jpeg"
private const val JpegQuality: Int = 85
private const val ScaleStep: Float = 0.75f

/**
 * In-memory image work: encodes a bitmap under the upload limit and burns opaque boxes into it.
 */
internal object LBFeedbackImageCodec {
    fun encode(
        bitmap: ImageBitmap,
        name: String = LBFeedbackAttachment.screenshotName(),
        maxBytes: Int = ScreenshotMaxBytes,
    ): LBFeedbackAttachment {
        var current = bitmap
        var bytes = encodeJpeg(current, JpegQuality)
        while (bytes.size > maxBytes && current.width > 1 && current.height > 1) {
            current = scale(current, ScaleStep)
            bytes = encodeJpeg(current, JpegQuality)
        }
        return LBFeedbackAttachment(bytes = bytes, name = name, mimeType = ScreenshotMimeType)
    }

    fun encode(encoded: ByteArray): LBFeedbackAttachment = encode(decodeOrThrow(encoded))

    fun redact(source: LBFeedbackAttachment, boxes: List<LBFeedbackRedactBox>): LBFeedbackAttachment {
        val image = decodeOrThrow(source.bytes)
        val target = ImageBitmap(image.width, image.height)
        val canvas = Canvas(target)
        canvas.drawImage(image, Offset.Zero, Paint())
        val black = Paint().apply { color = Color.Black }
        boxes.forEach {
            canvas.drawRect(
                left = it.left * image.width,
                top = it.top * image.height,
                right = it.right * image.width,
                bottom = it.bottom * image.height,
                paint = black,
            )
        }
        return encode(target, name = source.name)
    }

    private fun decodeOrThrow(encoded: ByteArray): ImageBitmap =
        decodeImage(encoded) ?: throw IllegalArgumentException("Not a decodable image")

    private fun scale(image: ImageBitmap, factor: Float): ImageBitmap {
        val width = (image.width * factor).toInt().coerceAtLeast(1)
        val height = (image.height * factor).toInt().coerceAtLeast(1)
        val target = ImageBitmap(width, height)
        Canvas(target).drawImageRect(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, height),
            paint = Paint(),
        )
        return target
    }
}
