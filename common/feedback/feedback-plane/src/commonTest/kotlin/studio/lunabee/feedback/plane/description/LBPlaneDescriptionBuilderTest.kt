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

package studio.lunabee.feedback.plane.description

import studio.lunabee.feedback.plane.fakeDraft
import studio.lunabee.feedback.test.fakeMetadata
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LBPlaneDescriptionBuilderTest {
    @Test
    fun escapesHtmlInBody() {
        val html = LBPlaneDescriptionBuilder.build(fakeDraft(body = "<script>&\"'"), fakeMetadata())

        assertContains(html, "<p>&lt;script&gt;&amp;&quot;&#39;</p>")
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun newlinesBecomeBreaks() {
        val html = LBPlaneDescriptionBuilder.build(fakeDraft(body = "a\nb"), fakeMetadata())

        assertContains(html, "<p>a<br>b</p>")
    }

    @Test
    fun reporterLineIsEscapedWhenSet() {
        val html = LBPlaneDescriptionBuilder.build(fakeDraft(reporter = "<Ada>"), fakeMetadata())

        assertContains(html, "<strong>Reporter:</strong> &lt;Ada&gt;")
    }

    @Test
    fun reporterLineIsAbsentWhenNull() {
        val html = LBPlaneDescriptionBuilder.build(fakeDraft(reporter = null), fakeMetadata())

        assertFalse(html.contains("Reporter"))
    }

    @Test
    fun metadataTableHoldsRowsAndEscapedExtras() {
        val html = LBPlaneDescriptionBuilder.build(fakeDraft(), fakeMetadata(extras = mapOf("route" to "<home>")))

        assertContains(html, "<tr><td>App version</td><td>1.2.3 (42)</td></tr>")
        assertContains(html, "<tr><td>Build variant</td><td>devBeta</td></tr>")
        assertContains(html, "<tr><td>Device</td><td>Google Pixel 9</td></tr>")
        assertContains(html, "<tr><td>OS</td><td>Android 16</td></tr>")
        assertContains(html, "<tr><td>Locale</td><td>fr-FR</td></tr>")
        assertContains(html, "<tr><td>Library version</td><td>1.0.0</td></tr>")
        assertContains(html, "<tr><td>Sent at (UTC)</td><td>1970-01-01T00:00:00Z</td></tr>")
        assertContains(html, "<tr><td>route</td><td>&lt;home&gt;</td></tr>")
    }
}
