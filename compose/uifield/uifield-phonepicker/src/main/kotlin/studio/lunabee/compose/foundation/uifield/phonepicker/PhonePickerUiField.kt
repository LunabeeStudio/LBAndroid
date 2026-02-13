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

package studio.lunabee.compose.foundation.uifield.phonepicker

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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.UiFieldOption
import studio.lunabee.compose.foundation.uifield.countrypicker.CountryPickerBottomSheetRenderer
import studio.lunabee.compose.foundation.uifield.countrypicker.CountrySearchDelegate
import studio.lunabee.compose.foundation.uifield.countrypicker.CountrySearchUiState
import studio.lunabee.compose.foundation.uifield.field.UiFieldError
import studio.lunabee.compose.foundation.uifield.field.style.DefaultUiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.text.TextUiField

@Suppress("LongParameterList")
class PhonePickerUiField(
    override val initialValue: CountryCodeFieldData,
    override var label: LbcTextSpec?,
    override var placeholder: LbcTextSpec?,
    override val id: String,
    override val savedStateHandle: SavedStateHandle,
    override val isFieldInError: (CountryCodeFieldData) -> UiFieldError?,
    uiFieldStyleData: UiFieldStyleData = DefaultUiFieldStyleData(),
    override val onValueChange: (CountryCodeFieldData) -> Unit,
    override val readOnly: Boolean = false,
    override val enabled: Boolean = true,
    private val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    private val keyboardActions: KeyboardActions = KeyboardActions.Default,
    private val phoneFieldRenderer: PhoneFieldRenderer,
    private val coroutineScope: CoroutineScope,
    private val countryPickerBottomSheetRenderer: CountryPickerBottomSheetRenderer,
) : TextUiField<CountryCodeFieldData>(uiFieldStyleData) {

    override val options: List<UiFieldOption> = emptyList()

    override fun formToDisplay(value: CountryCodeFieldData): TextFieldValue = TextFieldValue(value.phoneNumber)

    override fun saveToSavedStateHandle(value: CountryCodeFieldData, savedStateHandle: SavedStateHandle) {
        savedStateHandle["${id}_countryCode"] = value.countryCode
        savedStateHandle["${id}_phoneNumber"] = value.phoneNumber
    }

    override fun restoreFromSavedStateHandle(savedStateHandle: SavedStateHandle): CountryCodeFieldData? {
        val countryCode = savedStateHandle.get<String>("${id}_countryCode")
        val phoneNumber = savedStateHandle.get<String>("${id}_phoneNumber")

        return if (countryCode == null && phoneNumber == null) {
            null
        } else {
            CountryCodeFieldData(
                countryCode = savedStateHandle.get<String>("${id}_countryCode").orEmpty(),
                phoneNumber = savedStateHandle.get<String>("${id}_phoneNumber").orEmpty(),
            )
        }
    }

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

        phoneFieldRenderer.FieldContent(
            textField = { onFocusChange ->
                uiFieldStyleData.ComposeTextField(
                    value = formToDisplay(collectedValue),
                    onValueChange = {
                        value = value.copy(phoneNumber = it.text)
                        dismissError()
                    },
                    modifier = modifier
                        .onFocusEvent {
                            if (!it.hasFocus && hasBeenFocused) {
                                checkAndDisplayError()
                            } else {
                                hasBeenFocused = true
                                dismissError()
                            }
                            onFocusChange(it.hasFocus)
                        },
                    placeholder = placeholder,
                    label = label,
                    trailingIcon = if (options.isNotEmpty()) {
                        { options.forEach { it.Composable(modifier = Modifier) } }
                    } else {
                        null
                    },
                    visualTransformation = PhoneNumberVisualTransformation(
                        countryPhoneCode = value.countryCode,
                    ),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    maxLine = 1,
                    readOnly = readOnly,
                    enabled = enabled,
                    error = null, // We let the client handle the error message
                    interactionSource = null,
                )
            },
            openCountryPicker = { isPickerBottomSheetVisible = true },
            selectedCountry = uiState.selectedCountry,
            errorMessage = collectedError?.text,
        )

        if (isPickerBottomSheetVisible) {
            CountryPickerBottomSheet(delegate, uiState) { isPickerBottomSheetVisible = false }
        }

        LaunchedEffect(collectedValue.countryCode) {
            delegate.updateOnCountryCodeChange(collectedValue.countryCode)
        }

        LaunchedEffect(Unit) {
            delegate.initWithInitialCountryPhoneCode(collectedValue.countryCode)
        }
    }

    @Composable
    private fun CountryPickerBottomSheet(
        delegate: CountrySearchDelegate,
        uiState: CountrySearchUiState,
        dismiss: () -> Unit,
    ) {
        countryPickerBottomSheetRenderer.BottomSheetHolder(
            dismiss = {
                dismiss()
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
                                value = value.copy(countryCode = country.countryPhoneCode)
                                delegate.onCountrySelected(country)
                                dismiss()
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

    companion object {
        /**
         * Creates a [CountryCodeFieldData] instance by parsing a raw phone number string.
         *
         * This function attempts to parse the [rawPhoneNumber] using Google's `libphonenumber` library.
         * If successful, it extracts the national number and the country code.
         * If parsing fails (e.g., the format is invalid), it returns a [CountryCodeFieldData]
         * with the original [rawPhoneNumber] as the phone number and uses the [fallbackCountryCode].
         *
         * @param rawPhoneNumber The raw phone number string to parse. It may or may not include a country code.
         * @param fallbackCountryCode An optional country code to use if the [rawPhoneNumber] cannot be parsed.
         *   If null, the country code will be empty.
         * @return A [CountryCodeFieldData] instance containing the parsed phone number and country code,
         *   or the raw number and fallback code on failure.
         */
        fun initialValueFromRawPhoneNumber(
            rawPhoneNumber: String,
            fallbackCountryCode: String? = null,
        ): CountryCodeFieldData {
            try {
                val phoneNumber = PhoneNumberUtil.getInstance().parse(rawPhoneNumber, null)
                return CountryCodeFieldData(
                    phoneNumber = phoneNumber.nationalNumber.toString(),
                    countryCode = phoneNumber.countryCode.toString(),
                )
            } catch (_: Exception) {
                return CountryCodeFieldData(
                    phoneNumber = rawPhoneNumber,
                    countryCode = fallbackCountryCode.orEmpty(),
                )
            }
        }
    }
}
