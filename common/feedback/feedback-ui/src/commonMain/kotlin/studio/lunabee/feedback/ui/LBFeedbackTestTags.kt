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

/**
 * Test tags of the feedback screens, for UI tests and device automation.
 */
object LBFeedbackTestTags {
    /** Back action of the top bar. */
    const val BackButton: String = "feedback_back"

    /** "Bug" option of the type selector. */
    const val TypeBug: String = "feedback_type_bug"

    /** "Idea" option of the type selector. */
    const val TypeIdea: String = "feedback_type_idea"

    /** "Other" option of the type selector. */
    const val TypeOther: String = "feedback_type_other"

    /** Title field of the form. */
    const val TitleField: String = "feedback_title"

    /** Description field of the form. */
    const val BodyField: String = "feedback_body"

    /** "Your name" field of the form. */
    const val ReporterField: String = "feedback_reporter"

    /** Switch to attach the screenshot. */
    const val AttachToggle: String = "feedback_attach_toggle"

    /** "Pick from gallery" button. */
    const val PickImageButton: String = "feedback_pick_image"

    /** "My feedback" action of the form top bar. */
    const val ListButton: String = "feedback_list_button"

    /** "Redact" action of the screenshot preview. */
    const val RedactButton: String = "feedback_redact"

    /** "Remove" action of the screenshot preview. */
    const val RemoveScreenshotButton: String = "feedback_remove_screenshot"

    /** Send (or Save) button of the form. */
    const val SendButton: String = "feedback_send"

    /** Retry button shown after a failed send. */
    const val RetryButton: String = "feedback_retry"

    /** Button retrying only the failed attachments. */
    const val RetryAttachmentsButton: String = "feedback_retry_attachments"

    /** Close button shown after a partial failure. */
    const val CloseButton: String = "feedback_close"

    /** Undo button of the redact screen. */
    const val UndoButton: String = "feedback_undo"

    /** Done button of the redact screen. */
    const val DoneButton: String = "feedback_done"

    /** Drawing area of the redact screen. */
    const val RedactCanvas: String = "feedback_redact_canvas"

    /** Empty message of "My feedback". */
    const val ListEmpty: String = "feedback_list_empty"

    /** Tag of the remove button of the pending file named [name] in the form. */
    fun removeAttachmentButton(name: String): String = "feedback_remove_attachment_$name"

    /** Tag of the row of the item [remoteId] in the list. */
    fun listRow(remoteId: String): String = "feedback_row_$remoteId"

    /** Tag of the edit button of the item [remoteId] in the list. */
    fun editButton(remoteId: String): String = "feedback_edit_$remoteId"

    /** Tag of the delete button of the item [remoteId] in the list. */
    fun deleteButton(remoteId: String): String = "feedback_delete_$remoteId"
}
