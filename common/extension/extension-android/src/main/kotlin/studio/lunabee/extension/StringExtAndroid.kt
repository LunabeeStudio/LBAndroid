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
@file:JvmName("StringUtils")

package studio.lunabee.extension

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt

/**
 * Replace all bbCode tags by their html equivalent.
 */
fun String.bbCodeToHTML(): String {
    var html: String = this

    for ((key, value) in bbMap) {
        html = html.replace(key.toRegex(), value)
    }

    return html
}

private val bbMap: Map<String, String> by lazyFast {
    HashMap<String, String>().apply {
        this["(\r\n|\r|\n|\n\r)"] = "<br/>"
        this["\\[b\\](.+?)\\[/b\\]"] = "<b>$1</b>"
        this["\\[i\\](.+?)\\[/i\\]"] = "<span style='font-style:italic;'>$1</span>"
        this["\\[u\\](.+?)\\[/u\\]"] = "<span style='text-decoration:underline;'>$1</span>"
        this["\\[h1\\](.+?)\\[/h1\\]"] = "<h1>$1</h1>"
        this["\\[h2\\](.+?)\\[/h2\\]"] = "<h2>$1</h2>"
        this["\\[h3\\](.+?)\\[/h3\\]"] = "<h3>$1</h3>"
        this["\\[h4\\](.+?)\\[/h4\\]"] = "<h4>$1</h4>"
        this["\\[h5\\](.+?)\\[/h5\\]"] = "<h5>$1</h5>"
        this["\\[h6\\](.+?)\\[/h6\\]"] = "<h6>$1</h6>"
        this["\\[quote\\](.+?)\\[/quote\\]"] = "<blockquote>$1</blockquote>"
        this["\\[p\\](.+?)\\[/p\\]"] = "<p>$1</p>"
        this["\\[p=(.+?),(.+?)\\](.+?)\\[/p\\]"] = "<p style='text-indent:$1px;line-height:$2%;'>$3</p>"
        this["\\[center\\](.+?)\\[/center\\]"] = "<div align='center'>$1"
        this["\\[align=(.+?)\\](.+?)\\[/align\\]"] = "<div align='$1'>$2"
        this["\\[color=(.+?)\\](.+?)\\[/color\\]"] = "<span style='color:$1;'>$2</span>"
        this["\\[size=(.+?)\\](.+?)\\[/size\\]"] = "<span style='font-size:$1;'>$2</span>"
        this["\\[img\\](.+?)\\[/img\\]"] = "<img src='$1' />"
        this["\\[img=(.+?),(.+?)\\](.+?)\\[/img\\]"] = "<img width='$1' height='$2' src='$3' />"
        this["\\[email\\](.+?)\\[/email\\]"] = "<a href='mailto:$1'>$1</a>"
        this["\\[email=(.+?)\\](.+?)\\[/email\\]"] = "<a href='mailto:$1'>$2</a>"
        this["\\[url\\](.+?)\\[/url\\]"] = "<a href='$1'>$1</a>"
        this["\\[url=(.+?)\\](.+?)\\[/url\\]"] = "<a href='$1'>$2</a>"
        this["\\[youtube\\](.+?)\\[/youtube\\]"] = "<object width='640' height='380'>" +
            "<param name='movie' value='http://www.youtube.com/v/$1'></param>" +
            "<embed src='http://www.youtube.com/v/$1' type='application/x-shockwave-flash' width='640' height='380'>" +
            "</embed></object>"
        this["\\[video\\](.+?)\\[/video\\]"] = "<video src='$1' />"
    }
}

/**
 * Returns displayable styled text from the provided HTML string. Any &lt;img&gt; tags in the
 * HTML will display as a generic replacement image which your program can then go through and
 * replace with real images.
 *
 * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
 */
@Suppress("DEPRECATION")
fun String.fromHtml(): Spanned {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(this)
    }
}

/**
 * Convert a string with BBCode markups into a spanned String
 */
fun String.bbCodeToSpanned(): Spanned = this.bbCodeToHTML().fromHtml()

/**
 * Process a search string to apply a spanned style to a string if the search query is found inside
 * the string.
 *
 * @param treatedString The string where we want to apply the search and the style.
 * @param treatedSearchString The string that the user searched treated. If the [treatedSearchString]
 * is empty or is not contained in [treatedString] then the style will be applied to the whole receiver.
 * @param textColor Foreground color of the whole text.
 * @param substringColor Foreground color of the text that was found.
 * @param typeface The typeface style of the returned spannable string.
 *
 * @return A SpannableString highlighting the correct characters.
 */
fun String.colorSubstringForSearch(
    treatedString: String,
    treatedSearchString: String,
    @ColorInt textColor: Int,
    @ColorInt substringColor: Int,
    typeface: Int = Typeface.NORMAL,
): SpannableString {
    val spannable = setTypeface(typeface)

    spannable.setSpan(
        ForegroundColorSpan(textColor),
        0,
        this.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )

    if (treatedSearchString.isNotEmpty() && treatedString.contains(treatedSearchString)) {
        spannable.setSpan(
            ForegroundColorSpan(substringColor),
            treatedString.indexOf(treatedSearchString),
            treatedString.indexOf(treatedSearchString) + treatedSearchString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    return spannable
}

/**
 * Wrap a string into a typeFaceSpan
 */
fun String.setTypeface(typeface: Int): SpannableString = SpannableString(this).apply {
    setSpan(StyleSpan(typeface), 0, this.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}
