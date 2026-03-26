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

package studio.lunabee.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.launch
import studio.lunabee.compose.navigation.BottomSheetSceneStrategy.Companion.bottomSheet
import studio.lunabee.compose.navigation.utils.normalPopTransition
import studio.lunabee.compose.navigation.utils.normalPushTransition
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private data class BottomSheetContentState<T : Any>(
    val entry: NavEntry<T>,
    val depth: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    override val entries: List<NavEntry<T>>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onDismiss: () -> Unit,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val content: @Composable (() -> Unit) = {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Keep a single ModalBottomSheet alive and only swap the top-most destination inside it.
        val coroutineScope = rememberCoroutineScope()
        val topContent = BottomSheetContentState(
            entry = entries.last(),
            depth = entries.size,
        )
        val requestDismiss = {
            coroutineScope.launch {
                sheetState.hide()
                onDismiss()
            }
            Unit
        }
        Box(modifier = Modifier.fillMaxSize()) {
            ModalBottomSheet(
                onDismissRequest = requestDismiss,
                sheetState = sheetState,
                properties = modalBottomSheetProperties,
                dragHandle = { Spacer(modifier = Modifier.height(8.dp)) },
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            ) {
                val backStack = LocalBackStack.current

                backStack?.lastOrNull {
                    it.bottomSheetGroupId == entries.last().metadata[BottomSheetSceneStrategy.Companion.BottomSheetGroupIdKey]
                }?.let { entry ->
                    LocalPresenterRegistry.current?.get(entry.id)?.TopBar()
                }
                AnimatedContent(
                    targetState = topContent,
                    transitionSpec = {
                        if (targetState.depth >= initialState.depth) {
                            normalPushTransition()
                        } else {
                            normalPopTransition()
                        }
                    },
                ) { state ->
                    BackHandler {
                        if (entries.size > 1) {
                            onBack()
                        } else {
                            requestDismiss()
                        }
                    }
                    state.entry.Content()
                }
            }
        }
    }
}

/**
 * A [SceneStrategy] that displays entries that have added [bottomSheet] to their [NavEntry.metadata]
 * within a [ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val bottomSheetProperties = lastEntry.metadata[BottomSheetKey] ?: return null
        val currentBottomSheetGroupId = lastEntry.metadata[BottomSheetGroupIdKey] ?: return null

        // Split the back stack into:
        // - baseEntries: every destination that should stay underneath the top-most sheet
        // - bottomSheetEntries: only the trailing entries that belong to the current sheet group
        //
        // This is what lets `modal()` open a brand new sheet on top of another one:
        // the previous sheet entries stay in baseEntries, so Nav 3 can keep rendering them
        // underneath, while the new group becomes the only content of the new overlay scene.
        val bottomSheetStartIndex = entries.indexOfLast { entry ->
            entry.metadata[BottomSheetKey] == null ||
                entry.metadata[BottomSheetGroupIdKey] != currentBottomSheetGroupId
        } + 1
        val bottomSheetEntries = entries.drop(bottomSheetStartIndex)
        val baseEntries = entries.take(bottomSheetStartIndex)

        // OverlayScene must always sit above at least one underlying entry.
        // If the whole stack is made of sheet entries, let another SceneStrategy render it instead.
        if (baseEntries.isEmpty()) return null

        // Tell NavDisplay what "one back press" means for the current sheet group:
        // if the group contains multiple destinations, pop only the top destination inside it;
        // otherwise remove the whole top-most sheet and reveal whatever was underneath.
        val previousEntries = if (bottomSheetEntries.size > 1) {
            baseEntries + bottomSheetEntries.dropLast(1)
        } else {
            baseEntries
        }

        return bottomSheetProperties.let { properties ->
            @Suppress("UNCHECKED_CAST")
            BottomSheetScene(
                // The scene key must stay stable across pushes inside the sheet.
                // Using the first sheet entry keeps the same overlay alive while only its content changes.
                key = bottomSheetEntries.first().contentKey as T,
                previousEntries = previousEntries,
                overlaidEntries = baseEntries,
                entries = bottomSheetEntries,
                modalBottomSheetProperties = properties,
                onDismiss = {
                    // A full dismiss closes the whole modal flow, so remove every sheet destination.
                    repeat(bottomSheetEntries.size) {
                        onBack()
                    }
                },
                onBack = onBack,
            )
        }
    }

    companion object {
        private val DefaultBottomSheetProperties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
        )

        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [ModalBottomSheet].
         *
         * @param modalBottomSheetProperties properties that should be passed to the containing
         * [ModalBottomSheet].
         */
        fun bottomSheet(
            groupId: Uuid,
            modalBottomSheetProperties: ModalBottomSheetProperties = DefaultBottomSheetProperties,
        ): Map<String, Any> =
            metadata {
                put(BottomSheetKey, modalBottomSheetProperties)
                put(BottomSheetGroupIdKey, groupId)
            }

        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

        @OptIn(ExperimentalUuidApi::class)
        object BottomSheetGroupIdKey : NavMetadataKey<Uuid>
    }
}
