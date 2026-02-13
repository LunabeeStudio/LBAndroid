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

package studio.lunabee.compose.demo.uifield

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import studio.lunabee.compose.core.LbcImageSpec
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.countrypicker.CountryPickerBottomSheetRenderer
import studio.lunabee.compose.foundation.uifield.countrypicker.CountryPickerUiField
import studio.lunabee.compose.foundation.uifield.countrypicker.CountrySearchItem
import studio.lunabee.compose.foundation.uifield.countrypicker.SelectedCountry
import studio.lunabee.compose.foundation.uifield.field.UiFieldError
import studio.lunabee.compose.foundation.uifield.field.style.DefaultUiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.text.NormalUiTextField
import studio.lunabee.compose.foundation.uifield.field.text.PasswordUiTextField
import studio.lunabee.compose.foundation.uifield.field.text.option.password.PasswordVisibilityOptionData
import studio.lunabee.compose.foundation.uifield.field.time.DateAndHourUiField
import studio.lunabee.compose.foundation.uifield.field.time.DateUiField
import studio.lunabee.compose.foundation.uifield.field.time.option.date.DatePickerData
import studio.lunabee.compose.foundation.uifield.field.time.option.hour.HourPickerData
import studio.lunabee.compose.foundation.uifield.phonepicker.PhoneFieldRenderer
import studio.lunabee.compose.foundation.uifield.phonepicker.PhoneNumberValidator
import studio.lunabee.compose.foundation.uifield.phonepicker.PhonePickerUiField
import studio.lunabee.compose.image.LbcImage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun UiFieldsScreen(savedStateHandle: SavedStateHandle) {
    val normalUiTextField = rememberNormalUiTextField(savedStateHandle)
    val passwordUiTextField = rememberPasswordUiTextField(savedStateHandle)
    val dateAndHourUiField = rememberDateAndHourUiField(savedStateHandle)
    val dateUiField = rememberDateUiField(savedStateHandle)
    val readOnlyField = rememberReadOnlyField(savedStateHandle)
    val disabledField = rememberDisabledField(savedStateHandle)
    val disabledDateUiField = rememberDisabledDateUiField(savedStateHandle)
    val phonePickerField = rememberPhonePickerField(savedStateHandle)
    val countryPickerField = rememberCountryPickerField(savedStateHandle)

    val areFieldsInError by combine(
        normalUiTextField.isInError,
        passwordUiTextField.isInError,
        dateAndHourUiField.isInError,
        dateUiField.isInError,
    ) { error1, error2, error3, error4 ->
        error1 || error2 || error3 || error4
    }.collectAsState(initial = false)

    Column(
        modifier = Modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        dateAndHourUiField.Composable(modifier = Modifier.fillMaxWidth())
        normalUiTextField.Composable(modifier = Modifier.fillMaxWidth())
        passwordUiTextField.Composable(modifier = Modifier.fillMaxWidth())
        dateUiField.Composable(modifier = Modifier.fillMaxWidth())
        readOnlyField.Composable(modifier = Modifier.fillMaxWidth())
        disabledField.Composable(modifier = Modifier.fillMaxWidth())
        phonePickerField.Composable(modifier = Modifier.fillMaxWidth())
        countryPickerField.Composable(modifier = Modifier.fillMaxWidth())
        disabledDateUiField.Composable(modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                normalUiTextField.checkAndDisplayError()
                passwordUiTextField.checkAndDisplayError()
                dateAndHourUiField.checkAndDisplayError()
                dateUiField.checkAndDisplayError()
                phonePickerField.checkAndDisplayError()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Check error")
        }
        Button(
            onClick = {},
            enabled = !areFieldsInError,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Its All Good")
        }
    }
}

@Composable
private fun rememberCountryPickerField(savedStateHandle: SavedStateHandle): CountryPickerUiField = remember {
    CountryPickerUiField(
        initialValue = "FR",
        placeholder = LbcTextSpec.Raw(""),
        label = LbcTextSpec.Raw("Country"),
        isFieldInError = { null },
        id = "9",
        savedStateHandle = savedStateHandle,
        onValueChange = {},
        coroutineScope = CoroutineScope(Dispatchers.Default),
        countryPickerBottomSheetRenderer = object : CountryPickerBottomSheetRenderer {
            @Composable
            override fun BottomSheetHolder(
                dismiss: () -> Unit,
                searchField: @Composable (() -> Unit),
                countriesList: @Composable ((PaddingValues, LazyListState) -> Unit),
            ) {
                CountrySearchBottomSheetHolder(
                    dismiss = dismiss,
                    searchField = searchField,
                    countriesList = countriesList,
                )
            }

            @Composable
            override fun CountryRow(
                countrySearchItem: CountrySearchItem,
                searchedText: String,
                onClick: () -> Unit,
            ) {
                CountryBottomSheetCountryRow(countrySearchItem, searchedText, onClick)
            }

            override val searchedFieldLabel: LbcTextSpec = LbcTextSpec.Raw("Search for a country")
            override val searchFieldPlaceHolder: LbcTextSpec = LbcTextSpec.Raw("")
            override val searchFieldStyleData: UiFieldStyleData = DefaultUiFieldStyleData()
        },
    )
}

@Composable
private fun rememberPhonePickerField(savedStateHandle: SavedStateHandle): PhonePickerUiField = remember {
    PhonePickerUiField(
        initialValue = PhonePickerUiField.initialValueFromRawPhoneNumber("+3379881"),
        placeholder = LbcTextSpec.Raw(""),
        label = LbcTextSpec.Raw("Phone number"),
        isFieldInError = { phone ->
            UiFieldError(LbcTextSpec.Raw("Invalid phone")).takeIf {
                !PhoneNumberValidator
                    .isValid(phone.fullNumber())
            }
        },
        id = "8",
        savedStateHandle = savedStateHandle,
        onValueChange = {},
        coroutineScope = CoroutineScope(Dispatchers.Default),
        phoneFieldRenderer = object : PhoneFieldRenderer {
            @Composable
            override fun FieldContent(
                textField: @Composable (onFocusChange: (focused: Boolean) -> Unit) -> Unit,
                selectedCountry: SelectedCountry?,
                openCountryPicker: () -> Unit,
                errorMessage: LbcTextSpec?,
            ) {
                PhoneFieldContent(
                    textField = textField,
                    selectedCountry = selectedCountry,
                    openCountryPicker = openCountryPicker,
                    errorMessage = errorMessage,
                )
            }
        },
        countryPickerBottomSheetRenderer = object : CountryPickerBottomSheetRenderer {
            @Composable
            override fun BottomSheetHolder(
                dismiss: () -> Unit,
                searchField: @Composable (() -> Unit),
                countriesList: @Composable ((PaddingValues, LazyListState) -> Unit),
            ) {
                CountrySearchBottomSheetHolder(
                    dismiss = dismiss,
                    searchField = searchField,
                    countriesList = countriesList,
                )
            }

            @Composable
            override fun CountryRow(
                countrySearchItem: CountrySearchItem,
                searchedText: String,
                onClick: () -> Unit,
            ) {
                CountryBottomSheetCountryRow(countrySearchItem, searchedText, onClick)
            }

            override val searchedFieldLabel: LbcTextSpec = LbcTextSpec.Raw("Search for a country")
            override val searchFieldPlaceHolder: LbcTextSpec = LbcTextSpec.Raw("")
            override val searchFieldStyleData: UiFieldStyleData = DefaultUiFieldStyleData()
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun rememberDisabledDateUiField(savedStateHandle: SavedStateHandle): DateUiField = remember {
    DateUiField(
        initialValue = LocalDate.now(),
        placeholder = LbcTextSpec.Raw(""),
        enabled = false,
        label = LbcTextSpec.Raw("Disabled date uifield"),
        isFieldInError = {
            null
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.now().toEpochMilli() < utcTimeMillis
        },
        savedStateHandle = savedStateHandle,
        id = "7",
        datePickerData = DatePickerData(
            datePickerClickLabel = LbcTextSpec.Raw("Picker Date"),
            datePickerConfirmLabel = LbcTextSpec.Raw("Confirm"),
            datePickerCancelLabel = LbcTextSpec.Raw("Cancel"),
            icon = LbcImageSpec.KtImageVector(Icons.Default.DateRange),
        ),
    )
}

@Composable
private fun rememberDisabledField(savedStateHandle: SavedStateHandle): NormalUiTextField = remember {
    NormalUiTextField(
        initialValue = TextFieldValue("Disabled field"),
        placeholder = LbcTextSpec.Raw(""),
        label = LbcTextSpec.Raw("Disabled"),
        isFieldInError = { value ->
            if (value.text.isBlank()) UiFieldError(LbcTextSpec.Raw("Should not be null")) else null
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        savedStateHandle = savedStateHandle,
        id = "6",
        readOnly = true,
        enabled = false,
    )
}

@Composable
private fun rememberReadOnlyField(savedStateHandle: SavedStateHandle): NormalUiTextField = remember {
    NormalUiTextField(
        initialValue = TextFieldValue("Read only field"),
        placeholder = LbcTextSpec.Raw(""),
        label = LbcTextSpec.Raw("Read only"),
        isFieldInError = { value ->
            if (value.text.isBlank()) UiFieldError(LbcTextSpec.Raw("Should not be null")) else null
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        savedStateHandle = savedStateHandle,
        id = "5",
        readOnly = true,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun rememberDateUiField(savedStateHandle: SavedStateHandle): DateUiField = remember {
    DateUiField(
        initialValue = null,
        placeholder = LbcTextSpec.Raw("This is a Date UiField"),
        label = LbcTextSpec.Raw("Date in future"),
        isFieldInError = {
            null
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.now().toEpochMilli() < utcTimeMillis
        },
        savedStateHandle = savedStateHandle,
        id = "4",
        datePickerData = DatePickerData(
            datePickerClickLabel = LbcTextSpec.Raw("Picker Date"),
            datePickerConfirmLabel = LbcTextSpec.Raw("Confirm"),
            datePickerCancelLabel = LbcTextSpec.Raw("Cancel"),
            icon = LbcImageSpec.KtImageVector(Icons.Default.DateRange),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDateAndHourUiField(savedStateHandle: SavedStateHandle): DateAndHourUiField = remember {
    DateAndHourUiField(
        initialValue = LocalDateTime.now(),
        placeholder = LbcTextSpec.Raw("This is a Date and Hour UiField"),
        label = LbcTextSpec.Raw("Date and Hour"),
        isFieldInError = {
            if (it?.isAfter(LocalDateTime.now().plusDays(7)) == true) {
                UiFieldError(
                    LbcTextSpec
                        .Raw("The date can't be more than 7 days in the future"),
                )
            } else {
                null
            }
        },
        savedStateHandle = savedStateHandle,
        id = "3",
        datePickerData = DatePickerData(
            datePickerClickLabel = LbcTextSpec.Raw("Picker Date"),
            datePickerConfirmLabel = LbcTextSpec.Raw("Confirm"),
            datePickerCancelLabel = LbcTextSpec.Raw("Cancel"),
            icon = LbcImageSpec.KtImageVector(Icons.Default.DateRange),
        ),
        hourPickerData = HourPickerData(
            hourPickerConfirmLabel = LbcTextSpec.Raw("Confirm"),
            hourPickerCancelLabel = LbcTextSpec.Raw("Cancel"),
            hourPickerClickLabel = LbcTextSpec.Raw("Picker Hour"),
            icon = LbcImageSpec.KtImageVector(Icons.Default.AccessTime),
        ),
    )
}

@Composable
private fun rememberPasswordUiTextField(savedStateHandle: SavedStateHandle): PasswordUiTextField = remember {
    PasswordUiTextField(
        initialValue = "",
        placeholder = LbcTextSpec.Raw("This is a password Text Field"),
        label = LbcTextSpec.Raw("Password"),
        isFieldInError = { value ->
            if (value.length <
                6
            ) {
                UiFieldError(LbcTextSpec.Raw("At least 6 chars required"))
            } else {
                null
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        savedStateHandle = savedStateHandle,
        id = "2",
        visibilityOptionData = PasswordVisibilityOptionData(
            hidePasswordClickLabel = LbcTextSpec.Raw("Hide password"),
            showPasswordClickLabel = LbcTextSpec.Raw("Show password"),
            showIcon = LbcImageSpec.KtImageVector(Icons.Default.Visibility),
            hideIcon = LbcImageSpec.KtImageVector(Icons.Default.VisibilityOff),
        ),
    )
}

@Composable
private fun rememberNormalUiTextField(savedStateHandle: SavedStateHandle): NormalUiTextField = remember {
    NormalUiTextField(
        initialValue = TextFieldValue("Yes yes"),
        placeholder = LbcTextSpec.Raw("This is a normal text field"),
        label = LbcTextSpec.Raw("Normal"),
        isFieldInError = { value ->
            if (value.text.isBlank()) UiFieldError(LbcTextSpec.Raw("Should not be null")) else null
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        savedStateHandle = savedStateHandle,
        id = "1",
    )
}

@Composable
private fun PhoneFieldContent(
    textField: @Composable (onFocusChange: (focused: Boolean) -> Unit) -> Unit,
    selectedCountry: SelectedCountry?,
    openCountryPicker: () -> Unit,
    errorMessage: LbcTextSpec?,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            selectedCountry?.let {
                LbcImage(
                    imageSpec = selectedCountry.flagImage,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { openCountryPicker() },
                )
            }
            textField {}
        }
        errorMessage?.let {
            Text(
                text = errorMessage.string,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySearchBottomSheetHolder(
    dismiss: () -> Unit,
    searchField: @Composable (() -> Unit),
    countriesList: @Composable ((PaddingValues, LazyListState) -> Unit),
) {
    ModalBottomSheet(
        onDismissRequest = {
            dismiss()
        },
        sheetState = rememberModalBottomSheetState(),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            searchField()

            countriesList(
                PaddingValues(16.dp),
                rememberLazyListState(),
            )
        }
    }
}

@Composable
private fun CountryBottomSheetCountryRow(
    countrySearchItem: CountrySearchItem,
    searchedText: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.clickable { onClick() },
    ) {
        LbcImage(
            imageSpec = countrySearchItem.flag,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = countrySearchItem
                .getAnnotatedName(
                    searchedText = searchedText,
                    nonMatchingSpanStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface),
                    matchingSpanStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                ).annotated,
            modifier = Modifier
                .weight(1f),
        )
    }
}
