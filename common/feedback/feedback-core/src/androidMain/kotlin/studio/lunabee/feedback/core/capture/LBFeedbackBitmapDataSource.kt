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

package studio.lunabee.feedback.core.capture

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import java.io.ByteArrayOutputStream

private const val JpegQuality: Int = 85
private const val ScaleStep: Float = 0.75f
private const val TargetMaxBytes: Int = 4_500_000
private const val MimeType: String = "image/jpeg"

internal class LBFeedbackBitmapDataSource(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun encode(bitmap: Bitmap): LBFeedbackAttachment = withContext(dispatcher) {
        var scaled = bitmap
        var bytes = compress(scaled)
        while (bytes.size > TargetMaxBytes && scaled.width > 1 && scaled.height > 1) {
            scaled = Bitmap.createScaledBitmap(
                scaled,
                (scaled.width * ScaleStep).toInt().coerceAtLeast(1),
                (scaled.height * ScaleStep).toInt().coerceAtLeast(1),
                true,
            )
            bytes = compress(scaled)
        }
        LBFeedbackAttachment(bytes = bytes, name = LBFeedbackAttachment.screenshotName(), mimeType = MimeType)
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)
        return output.toByteArray()
    }
}
