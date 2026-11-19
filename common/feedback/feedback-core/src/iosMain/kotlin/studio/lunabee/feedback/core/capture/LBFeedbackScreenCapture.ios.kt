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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIWindow
import platform.posix.memcpy
import studio.lunabee.feedback.core.model.LBFeedbackAttachment

private const val JpegQuality: Double = 0.9
private const val FileName: String = "screenshot.jpg"
private const val MimeType: String = "image/jpeg"

actual object LBFeedbackScreenCapture {
    actual suspend fun capture(): LBFeedbackAttachment? {
        val window = UIApplication.sharedApplication.keyWindow ?: return null
        val bytes = renderJpeg(window)?.takeIf { it.size <= LBFeedbackAttachment.MaxBytes } ?: return null
        return LBFeedbackAttachment(bytes = bytes, name = FileName, mimeType = MimeType)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun renderJpeg(window: UIWindow): ByteArray? {
        val image = UIGraphicsImageRenderer(bounds = window.bounds).imageWithActions {
            window.drawViewHierarchyInRect(window.bounds, afterScreenUpdates = false)
        }
        val data: NSData = UIImageJPEGRepresentation(image, JpegQuality) ?: return null
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }
}
