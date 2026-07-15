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

@file:Suppress("unused")
@file:JvmName("ContextUtils")

package studio.lunabee.extension.content

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.PluralsRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import studio.lunabee.extension.R
import studio.lunabee.extension.content.res.getColorStateList
import studio.lunabee.extension.content.res.getDrawable
import studio.lunabee.extension.content.res.use

/**
 * Check if the night theme is enabled.
 *
 * @return true if [Configuration.UI_MODE_NIGHT_MASK] is set. Else returns false.
 */
fun Context.isNightThemeEnabled(): Boolean =
    resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Found and return the Color Int corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource color we are looking for.
 * @return The Color Int mapped to the [attributeRes].
 */
@ColorInt
fun Context.colorFromAttribute(@AttrRes attributeRes: Int): Int {
    return valueFromAttribute(attributeRes) {
        getColor(0, 0)
    }
}

/**
 * Found and return the ColorStateList corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource color we are looking for.
 * @return The ColorStateList mapped to the [attributeRes].
 */
fun Context.colorStateListFromAttribute(@AttrRes attributeRes: Int): ColorStateList? {
    return valueFromAttribute(attributeRes) {
        getColorStateList(this@colorStateListFromAttribute, 0)
    }
}

/**
 * Found and return the integer corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource integer we are looking for.
 * @return The integer mapped to the [attributeRes].
 */
fun Context.integerFromAttribute(@AttrRes attributeRes: Int): Int {
    return valueFromAttribute(attributeRes) {
        getInteger(0, 0)
    }
}

/**
 * Found and return the float corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource float we are looking for.
 * @return The float mapped to the [attributeRes].
 */
fun Context.floatFromAttribute(@AttrRes attributeRes: Int): Float {
    return valueFromAttribute(attributeRes) {
        getFloat(0, 0f)
    }
}

/**
 * Found and return the boolean corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource boolean we are looking for.
 * @return The boolean mapped to the [attributeRes].
 */
fun Context.booleanFromAttribute(@AttrRes attributeRes: Int): Boolean {
    return valueFromAttribute(attributeRes) {
        getBoolean(0, false)
    }
}

/**
 * Found and return the drawable corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource drawable we are looking for.
 * @return The drawable mapped to the [attributeRes].
 */
fun Context.drawableFromAttribute(@AttrRes attributeRes: Int): Drawable? {
    return valueFromAttribute(attributeRes) {
        getDrawable(this@drawableFromAttribute, 0)
    }
}

/**
 * Found and return the font corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource font we are looking for.
 * @return The font mapped to the [attributeRes].
 */
@RequiresApi(Build.VERSION_CODES.O)
fun Context.fontFromAttribute(@AttrRes attributeRes: Int): Typeface? {
    return valueFromAttribute(attributeRes) {
        getFont(0)
    }
}

/**
 * Found and return the dimension corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource dimension we are looking for.
 * @return The dimension mapped to the [attributeRes] in pixels.
 */
fun Context.dimensionFromAttribute(@AttrRes attributeRes: Int): Int {
    return valueFromAttribute(attributeRes) {
        getDimensionPixelSize(0, 0)
    }
}

/**
 * Found and return the resource id corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource we are looking for.
 * @return The resource id mapped to the [attributeRes].
 */
fun Context.resourceIdFromAttributeOrNull(@AttrRes attributeRes: Int): Int? {
    return valueFromAttribute(attributeRes) {
        getResourceId(0, -1).takeIf { it != -1 }
    }
}

/**
 * Found and return the value corresponding to the [attributeRes] in the theme attributes.
 *
 * @param attributeRes The attribute resource value we are looking for.
 * @return The value found
 */
@SuppressLint("Recycle")
private inline fun <T> Context.valueFromAttribute(
    @AttrRes attributeRes: Int,
    block: TypedArray.() -> T,
): T {
    val typedValue = TypedValue()
    return obtainStyledAttributes(typedValue.data, intArrayOf(attributeRes)).use { it.block() }
}

/**
 * Found and return the value corresponding to the theme `colorPrimary` attributes.
 *
 * @return The color int of the theme `colorPrimary`.
 */
@ColorInt
fun Context.getColorPrimary() = colorFromAttribute(androidx.appcompat.R.attr.colorPrimary)

/**
 * Found and return the value corresponding to the theme `colorAccent` attributes.
 *
 * @return The color int of the theme `colorAccent`.
 */
@ColorInt
fun Context.getColorAccent() = colorFromAttribute(androidx.appcompat.R.attr.colorAccent)

/**
 * Found and return the value corresponding to the theme `colorSecondary` attributes.
 *
 * @return The color int of the theme `colorSecondary`.
 */
@ColorInt
fun Context.getColorSecondary() = colorFromAttribute(com.google.android.material.R.attr.colorSecondary)

/**
 * Convert an hexadecimal [unicode] into a string.
 * Be mindful that this function is not compatible with devices that don't have the unicode character
 * in their font.
 *
 * Ex:
 * 0x1F601 => 😁
 *
 *
 * @return The unicode character in a string
 */
fun Context.getEmojiFor(unicode: Int): String = String(Character.toChars(unicode))

/**
 * Start an explicit intent to send a email to [emailAddress]
 *
 * @param emailAddress The address to send the email
 * @param subject The subject to use in the email
 * @param body The body to use in the email
 */
fun Context.startEmailIntent(
    emailAddress: String,
    subject: String? = null,
    body: String? = null,
) {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("mailto:")
    intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
    if (subject != null) {
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    if (body != null) {
        intent.putExtra(Intent.EXTRA_TEXT, body)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.lbe_intent_send_email)))
}

/**
 * Start a call to [phoneNumber]
 *
 * @param phoneNumber The phone number to contact
 */
fun Context.startPhoneIntent(phoneNumber: String) {
    startActivity(getPhoneIntent(phoneNumber))
}

/**
 * Start an explicit intent to write a sms to [phoneNumber]
 *
 * @param phoneNumber The phone number to contact
 */
fun Context.startSMSIntent(phoneNumber: String) {
    startActivity(getSMSIntent(phoneNumber))
}

/**
 * Start an explicit intent to contact [phoneNumber]
 *
 * @param phoneNumber The phone number to contact
 */
fun Context.startPhoneAndSMSIntent(phoneNumber: String) {
    val chooser = Intent.createChooser(
        getPhoneIntent(phoneNumber),
        getString(R.string.lbe_intent_phone_sms),
    )
    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(getSMSIntent(phoneNumber)))
    startActivity(chooser)
}

/**
 * Start an explicit intent with text to share
 *
 * @param text The text to share
 * @param intentSender An [IntentSender] to be notify of user choice (API 22+)
 */
fun Context.startTextIntent(text: String, intentSender: IntentSender? = null) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }

    val shareIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        Intent.createChooser(sendIntent, null, intentSender)
    } else {
        Intent.createChooser(sendIntent, null)
    }
    startActivity(shareIntent)
}

/**
 * Start an explicit intent to open the app settings
 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", packageName, null)
    startActivity(intent)
}

/**
 * Returns a [ColorStateList] that can be used as a colored raised button's background tint.
 * Note that this code makes use of the `android.support.v4.graphics.ColorUtils`
 * utility class.
 */
fun Context.forColoredRaisedButton(@ColorInt backgroundColor: Int): ColorStateList {
    val numStates = 2

    val states = arrayOfNulls<IntArray>(numStates)
    val colors = IntArray(numStates)

    var i = 0

    states[i] = DISABLED_STATE_SET
    colors[i] = getDisabledButtonBackgroundColor()
    i++

    states[i] = EMPTY_STATE_SET
    colors[i] = backgroundColor

    return ColorStateList(states, colors)
}

/**
 * Returns a [ColorStateList] that can be used as a colored flat button's text tint.
 * Note that this code makes use of the `android.support.v4.graphics.ColorUtils`
 * utility class.
 */
fun Context.forColoredFlatButton(@ColorInt color: Int): ColorStateList {
    val numStates = 2

    val states = arrayOfNulls<IntArray>(numStates)
    val colors = IntArray(numStates)

    var i = 0

    states[i] = DISABLED_STATE_SET
    colors[i] = getDisabledButtonTextColor()
    i++

    states[i] = EMPTY_STATE_SET
    colors[i] = color

    return ColorStateList(states, colors)
}

/**
 * Returns the theme-dependent ARGB background color to use for disabled buttons.
 */
@ColorInt
private fun Context.getDisabledButtonBackgroundColor(): Int {
    // Extract the disabled alpha to apply to the button using the context's theme.
    // (0.26f for light themes and 0.30f for dark themes).
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.disabledAlpha, tv, true)
    val disabledAlpha = tv.float

    // Use the disabled alpha factor and the button's default normal color
    // to generate the button's disabled background color.
    val colorButtonNormal = getThemeAttrColor(androidx.appcompat.R.attr.colorButtonNormal)
    val originalAlpha = Color.alpha(colorButtonNormal)
    return ColorUtils.setAlphaComponent(
        colorButtonNormal,
        Math.round(originalAlpha * disabledAlpha),
    )
}

/**
 * Returns the theme-dependent ARGB text color to use for disabled buttons.
 */
@ColorInt
private fun Context.getDisabledButtonTextColor(): Int {
    // Extract the disabled alpha to apply to the button using the context's theme.
    // (0.26f for light themes and 0.30f for dark themes).
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.disabledAlpha, tv, true)
    val disabledAlpha = tv.float

    // Use the disabled alpha factor and the button's default normal color
    // to generate the button's disabled background color.
    val textColorSecondary = getThemeAttrColor(android.R.attr.textColorSecondary)
    val originalAlpha = Color.alpha(textColorSecondary)
    return ColorUtils.setAlphaComponent(
        textColorSecondary,
        Math.round(originalAlpha * disabledAlpha),
    )
}

/**
 * Returns the theme-dependent ARGB color that results when colorControlHighlight is drawn
 * on top of the provided background color.
 */
@ColorInt
private fun Context.getHighlightedBackgroundColor(@ColorInt backgroundColor: Int): Int {
    val colorControlHighlight = getThemeAttrColor(androidx.appcompat.R.attr.colorControlHighlight)
    return ColorUtils.compositeColors(colorControlHighlight, backgroundColor)
}

/** Returns the theme-dependent ARGB color associated with the provided theme attribute.  */
@SuppressLint("Recycle")
@ColorInt
private fun Context.getThemeAttrColor(@AttrRes attr: Int): Int {
    var color = 0
    obtainStyledAttributes(null, intArrayOf(attr)).use { typedArray ->
        color = typedArray.getColor(0, 0)
    }
    return color
}

private val DISABLED_STATE_SET = intArrayOf(-android.R.attr.state_enabled)
private val PRESSED_STATE_SET = intArrayOf(android.R.attr.state_pressed)
private val FOCUSED_STATE_SET = intArrayOf(android.R.attr.state_focused)
private val EMPTY_STATE_SET = IntArray(0)

private fun Context.getPhoneIntent(
    phoneNumber: String,
): Intent = Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:$phoneNumber"))

private fun Context.getSMSIntent(
    phoneNumber: String,
): Intent = Intent(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:$phoneNumber"))

/**
 * @see android.content.res.Resources.getQuantityString
 */
fun Context.getQuantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String =
    resources.getQuantityString(id, quantity, *formatArgs)

/**
 *  Forcibly show the keyboard, it will be not be closed until the user explicitly do so.
 *
 *  @receiver The context where the keyboard will be shown.
 */
@Deprecated("Use showSoftKeyBoard(View) instead", ReplaceWith("showSoftKeyBoard(view)"))
fun Context.showSoftKeyBoard() {
    @Suppress("DEPRECATION")
    ContextCompat.getSystemService(this, InputMethodManager::class.java)
        ?.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY)
}

fun Context.showSoftKeyBoard(view: View) {
    if (view.requestFocus()) {
        ContextCompat.getSystemService(this, InputMethodManager::class.java)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}

val Context.versionCode: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
    }

val Context.versionName: String?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)).versionName
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName
    }
