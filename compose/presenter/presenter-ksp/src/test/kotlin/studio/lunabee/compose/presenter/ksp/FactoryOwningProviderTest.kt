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

package studio.lunabee.compose.presenter.ksp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryOwningProviderTest {
    @Test
    fun detect_factory_owning_provider_on_classpath_test() {
        val found = hasFactoryOwningProvider(
            providerNames = listOf("com.example.Absent", "com.example.Present"),
            isOnClasspath = { it == "com.example.Present" },
        )

        assertTrue(found)
    }

    @Test
    fun detect_no_factory_owning_provider_test() {
        val found = hasFactoryOwningProvider(
            providerNames = listOf("com.example.Absent"),
            isOnClasspath = { false },
        )

        assertFalse(found)
    }

    @Test
    fun hilt_provider_absent_from_ksp_module_classpath_test() {
        assertFalse(hasFactoryOwningProvider())
    }
}
