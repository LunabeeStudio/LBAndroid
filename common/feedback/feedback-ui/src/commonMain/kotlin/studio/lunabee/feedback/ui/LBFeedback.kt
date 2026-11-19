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

package studio.lunabee.feedback.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.update
import studio.lunabee.feedback.core.client.LBFeedbackClient
import studio.lunabee.feedback.core.config.LBFeedbackConfig
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.store.LBFeedbackStore
import studio.lunabee.feedback.core.transport.LBFeedbackTransport

/**
 * Entry point of the in-app feedback library. Call [install] once at app start, before showing any feedback screen.
 *
 * No dependency injection framework is needed: [install] builds the object graph by hand.
 */
object LBFeedback {
    private var graph: LBFeedbackGraph? = null

    /**
     * Headless API, for apps that mix the built-in screens with their own. The built-in screens use it too.
     */
    val client: LBFeedbackClient
        get() = graph().client

    /**
     * Wires the library, screens inherit the surrounding `MaterialTheme`.
     *
     * @param config app version, build variant and metadata extras
     * @param transport where feedback is sent, for example the Plane transport of `feedback-plane`
     * @param store local memory of sent items, see `lbFeedbackStore` of each platform
     */
    fun install(
        config: LBFeedbackConfig,
        transport: LBFeedbackTransport,
        store: LBFeedbackStore,
    ) {
        install(config = config, transport = transport, store = store, themeWrapper = { it() })
    }

    /**
     * Wires the library.
     *
     * @param config app version, build variant and metadata extras
     * @param transport where feedback is sent, for example the Plane transport of `feedback-plane`
     * @param store local memory of sent items, see `lbFeedbackStore` of each platform
     * @param themeWrapper wraps the content of screens hosted by the library, to apply the app theme
     */
    fun install(
        config: LBFeedbackConfig,
        transport: LBFeedbackTransport,
        store: LBFeedbackStore,
        themeWrapper: @Composable (content: @Composable () -> Unit) -> Unit,
    ) {
        graph = LBFeedbackGraph(
            client = LBFeedbackClient(config = config, transport = transport, store = store),
            themeWrapper = themeWrapper,
        )
    }

    /**
     * Applies the theme wrapper given to [install] around [content]. Used by screens the library hosts itself.
     */
    @Composable
    fun Themed(content: @Composable () -> Unit) {
        graph().themeWrapper(content)
    }

    /**
     * Keeps [bitmap] in memory as the screenshot of the next feedback, replacing any previous one. Nothing is written to
     * disk. The image is re-encoded off the main thread to stay under the upload limit.
     */
    suspend fun setScreenshot(bitmap: ImageBitmap) {
        val graph = graph()
        graph.pending.screenshot.value = graph.imageData.encode(bitmap)
    }

    /**
     * Same as [setScreenshot] for an encoded image (PNG, JPEG), for example one picked from the gallery.
     *
     * @return `false` when the bytes are not a decodable image, the pending screenshot is left as it was then
     */
    suspend fun setScreenshot(encoded: ByteArray): Boolean {
        val graph = graph()
        val attachment = graph.imageData.encode(encoded) ?: return false
        graph.pending.screenshot.value = attachment
        return true
    }

    /**
     * Drops the pending screenshot.
     */
    fun clearScreenshot() {
        graph().pending.screenshot.value = null
    }

    /**
     * Adds [attachment] to the files that go with the next feedback, replacing a pending file of the same name. The built-in
     * form lists the files and lets the user remove each one. Call it before opening the form or while it is open. Files
     * stay in memory only, are cleared once the feedback is sent, and reach the remote queue as they are: they must never
     * carry user data. Each file is at most [LBFeedbackAttachment.MaxBytes].
     */
    fun addAttachment(attachment: LBFeedbackAttachment) {
        graph().pending.files.update { files -> files.filterNot { it.name == attachment.name } + attachment }
    }

    /**
     * Removes the pending file named [name].
     */
    fun removeAttachment(name: String) {
        graph().pending.files.update { files -> files.filterNot { it.name == name } }
    }

    /**
     * Removes every file added with [addAttachment]. The screenshot is left alone, see [clearScreenshot].
     */
    fun clearAttachments() {
        graph().pending.files.value = emptyList()
    }

    internal fun graph(): LBFeedbackGraph = checkNotNull(graph) { "LBFeedback.install must be called first" }
}
