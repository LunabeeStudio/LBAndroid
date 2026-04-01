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

package studio.lunabee.extension.content

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class SharedPreferencesExtTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun putAndGetData() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        sharedPreferences.edit {
            putData("data_key", data)
        }
        assertTrue { data.contentEquals(sharedPreferences.getData("data_key", null)) }
    }
}
