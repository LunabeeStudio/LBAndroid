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

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.hbb20.CCPCountry
import com.hbb20.CountryCodePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.lunabee.compose.core.LbcImageSpec
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.countrypicker.ext.normalized
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.text.NormalUiTextField
import java.util.Locale

class CountrySearchDelegate(
    savedStateHandle: SavedStateHandle,
    searchFieldLabel: LbcTextSpec?,
    searchFieldPlaceHolder: LbcTextSpec?,
    searchFieldStyleData: UiFieldStyleData,
    private val coroutineScope: CoroutineScope,
    context: Context,
) {

    private var allCountryCodes: List<CountrySearchItem> =
        CCPCountry
            .getLibraryMasterCountryList(
                context,
                CountryCodePicker.Language
                    .forCountryNameCode(Locale.getDefault().language),
            ).map { country ->
                CountrySearchItem(
                    name = country.name,
                    countryPhoneCode = country.phoneCode,
                    flag = LbcImageSpec.ImageDrawable(country.flagID),
                    isSelected = false,
                    isoName = country.nameCode,
                )
            }

    private val _uiState: MutableStateFlow<CountrySearchUiState> by lazy {
        MutableStateFlow(
            CountrySearchUiState(
                countryCodesToDisplay = allCountryCodes,
                searchedText = "",
                selectedCountry = null,
            ),
        )
    }

    val uiState: StateFlow<CountrySearchUiState> by lazy { _uiState.asStateFlow() }

    val searchUiField: NormalUiTextField by lazy {
        NormalUiTextField(
            id = SearchString,
            initialValue = TextFieldValue(),
            label = searchFieldLabel,
            placeholder = searchFieldPlaceHolder,
            isFieldInError = { null },
            savedStateHandle = savedStateHandle,
            uiFieldStyleData = searchFieldStyleData,
            onValueChange = { onSearchedTextChange(it.text) },
        )
    }

    private fun onSearchedTextChange(value: String) {
        updateUiStateWithSearch(value)
    }

    private fun updateUiStateWithSearch(searchedText: String) {
        _uiState.value = uiState.value.copy(
            countryCodesToDisplay = allCountryCodes.filter { country ->
                country.name
                    .normalized()
                    .contains(searchedText.normalized())
            },
            searchedText = searchedText,
        )
    }

    internal fun initWithInitialCountryName(
        isoCountry: String,
    ) {
        coroutineScope.launch {
            allCountryCodes
                .firstOrNull { it.isoName == isoCountry }
                ?.let { initialCountry ->
                    _uiState.value = uiState.value.copy(
                        selectedCountry = SelectedCountry(
                            name = initialCountry.name,
                            countryPhoneCode = initialCountry.countryPhoneCode,
                            flagImage = initialCountry.flag,
                        ),
                    )
                }

            updateUiStateWithSearch(searchUiField.value.text)
        }
    }

    fun initWithInitialCountryPhoneCode(
        countryPhoneCode: String,
    ) {
        coroutineScope.launch {
            allCountryCodes
                .firstOrNull { it.countryPhoneCode == countryPhoneCode }
                ?.let { initialCountry ->
                    _uiState.value = uiState.value.copy(
                        selectedCountry = SelectedCountry(
                            name = initialCountry.name,
                            countryPhoneCode = initialCountry.countryPhoneCode,
                            flagImage = initialCountry.flag,
                        ),
                    )
                }

            updateUiStateWithSearch(searchUiField.value.text)
        }
    }

    fun onCountrySelected(searchItem: CountrySearchItem) {
        _uiState.value = _uiState.value.copy(
            selectedCountry = SelectedCountry(
                name = searchItem.name,
                flagImage = searchItem.flag,
                countryPhoneCode = searchItem.countryPhoneCode,
            ),
        )
    }

    fun updateOnCountryCodeChange(countryCode: String) {
        if (uiState.value.selectedCountry?.countryPhoneCode != countryCode) {
            allCountryCodes.firstOrNull { it.countryPhoneCode == countryCode }?.let { country ->
                _uiState.value = _uiState.value.copy(
                    selectedCountry = SelectedCountry(
                        name = country.name,
                        flagImage = country.flag,
                        countryPhoneCode = country.countryPhoneCode,
                    ),
                )
            }
        }
    }

    internal fun displayNameFromIso(iso: String): String = allCountryCodes
        .firstOrNull { it.isoName == iso }
        ?.name
        .orEmpty()
}

private const val SearchString: String = "SearchUiField"
