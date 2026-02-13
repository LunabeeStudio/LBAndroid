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

package studio.lunabee.compose.foundation.uifield.countrypicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.UiFieldOption
import studio.lunabee.compose.foundation.uifield.field.UiFieldError
import studio.lunabee.compose.foundation.uifield.field.style.DefaultUiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.text.TextUiField

/**
 * A [UiField] implementation for selecting a country from a list presented in a bottom sheet.
 *
 * This field displays the selected country's name. When the user focuses on the field, a bottom sheet appears, allowing them
 * to search for and select a country. The field's value is the ISO 3166-1 alpha-2 country code (e.g., "US", "FR").
 *
 * It uses the [hbb20.CountryCodePicker-Android](https://github.com/hbb20/CountryCodePicker-Android) library for country data.
 *
 * @param initialValue The initial ISO 3166-1 alpha-2 country code to be displayed.
 * @param label The label for the text field, as a [LbcTextSpec].
 * @param placeholder The placeholder text to be displayed when the field is empty, as a [LbcTextSpec].
 * @param id A unique identifier for the field, used for state saving.
 * @param savedStateHandle The [SavedStateHandle] used to persist the field's state across process death.
 * @param isFieldInError A lambda function that determines if the current value is in an error state. It should return a
 * [UiFieldError] if there is an error, or `null` otherwise.
 * @param uiFieldStyleData The styling data for the text field, implementing [UiFieldStyleData]. Defaults to [DefaultUiFieldStyleData].
 * @param onValueChange A callback that is invoked when the selected country changes. The new value is the country's ISO code.
 * @param readOnly A boolean indicating whether the field is read-only. If `true`, the user cannot change the value.
 * @param enabled A boolean indicating whether the field is enabled. If `false`, the field is disabled and does not respond to
 * user input.
 * @param coroutineScope The [CoroutineScope] used for managing background operations, such as country search.
 */
@Suppress("LongParameterList")
class CountryPickerUiField(
    override val initialValue: String,
    override var label: LbcTextSpec?,
    override var placeholder: LbcTextSpec?,
    override val id: String,
    override val savedStateHandle: SavedStateHandle,
    override val isFieldInError: (String?) -> UiFieldError?,
    uiFieldStyleData: UiFieldStyleData = DefaultUiFieldStyleData(),
    override val onValueChange: (String) -> Unit,
    override val readOnly: Boolean = false,
    override val enabled: Boolean = true,
    private val coroutineScope: CoroutineScope,
    private val countryPickerBottomSheetRenderer: CountryPickerBottomSheetRenderer,
    private val trailingIcon: @Composable (() -> Unit)? = null,
) : TextUiField<String>(uiFieldStyleData) {

    override val options: List<UiFieldOption> = emptyList()

    override fun formToDisplay(value: String): TextFieldValue = TextFieldValue(value)

    override fun saveToSavedStateHandle(value: String, savedStateHandle: SavedStateHandle) {
        savedStateHandle[id] = value
    }

    override fun restoreFromSavedStateHandle(savedStateHandle: SavedStateHandle): String? = savedStateHandle.get<String>(id)

    @Composable
    override fun Composable(
        modifier: Modifier,
        uiFieldStyleData: UiFieldStyleData,
    ) {
        val context = LocalContext.current
        val delegate = remember {
            CountrySearchDelegate(
                savedStateHandle = savedStateHandle,
                searchFieldLabel = countryPickerBottomSheetRenderer.searchedFieldLabel,
                searchFieldPlaceHolder = countryPickerBottomSheetRenderer.searchFieldPlaceHolder,
                searchFieldStyleData = countryPickerBottomSheetRenderer.searchFieldStyleData,
                coroutineScope = coroutineScope,
                context = context,
            )
        }
        val uiState: CountrySearchUiState by delegate.uiState.collectAsStateWithLifecycle()

        val collectedValue by mValue.collectAsState()

        val collectedError by error.collectAsState()
        var isPickerBottomSheetVisible by rememberSaveable { mutableStateOf(false) }

        val focusManager = LocalFocusManager.current

        uiFieldStyleData.ComposeTextField(
            value = TextFieldValue(delegate.displayNameFromIso(value)),
            onValueChange = {
                dismissError()
            },
            modifier = modifier
                .onFocusEvent {
                    if (it.hasFocus) {
                        isPickerBottomSheetVisible = true
                    }
                    if (!it.hasFocus && hasBeenFocused) {
                        checkAndDisplayError()
                    } else {
                        hasBeenFocused = true
                        dismissError()
                    }
                },
            placeholder = placeholder,
            label = label,
            trailingIcon = trailingIcon,
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions.Default,
            maxLine = 1,
            readOnly = readOnly,
            enabled = enabled,
            error = collectedError,
            interactionSource = null,
        )

        if (isPickerBottomSheetVisible) {
            focusManager.clearFocus(true)

            countryPickerBottomSheetRenderer.BottomSheetHolder(
                dismiss = {
                    isPickerBottomSheetVisible = false
                },
                searchField = {
                    delegate.searchUiField.Composable(
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                countriesList = { contentPadding, lazyListState ->
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = contentPadding,
                        state = lazyListState,
                    ) {
                        // Top anchor
                        item {
                            Box(modifier = Modifier.size(1.dp))
                        }
                        items(uiState.countryCodesToDisplay) { country ->
                            countryPickerBottomSheetRenderer.CountryRow(
                                countrySearchItem = country,
                                searchedText = uiState.searchedText,
                                onClick = {
                                    value = country.isoName
                                    delegate.onCountrySelected(country)
                                    isPickerBottomSheetVisible = false
                                    dismissError()
                                },
                            )
                        }
                    }

                    LaunchedEffect(uiState.countryCodesToDisplay) {
                        lazyListState.scrollToItem(0)
                    }
                },
            )
        }

        LaunchedEffect(Unit) {
            delegate.initWithInitialCountryName(collectedValue)
        }
    }
}
