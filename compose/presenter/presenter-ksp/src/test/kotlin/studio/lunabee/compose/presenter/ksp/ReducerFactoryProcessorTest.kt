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

import com.google.devtools.ksp.processing.PlatformInfo
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducerFactoryProcessorTest {
    @Test
    fun should_generate_koin_module_for_single_platform_compilation_test() {
        assertTrue(
            shouldGenerateKoinModuleForCompilation(
                platforms = listOf(FakePlatformInfo(platformName = "JVM")),
            ),
        )
    }

    @Test
    fun should_not_generate_koin_module_for_common_metadata_compilation_test() {
        assertFalse(shouldGenerateKoinModuleForCompilation(platforms = emptyList()))
    }

    @Test
    fun should_not_generate_koin_module_for_multi_platform_metadata_compilation_test() {
        assertFalse(
            shouldGenerateKoinModuleForCompilation(
                platforms = listOf(
                    FakePlatformInfo(platformName = "JVM"),
                    FakePlatformInfo(platformName = "Native"),
                ),
            ),
        )
    }

    @Test
    fun resolve_module_root_package_name_uses_configured_package_test() {
        assertEquals(
            "studio.lunabee.compose.demo",
            resolveModuleRootPackageName(
                configuredPackageName = "studio.lunabee.compose.demo",
                sourcePackageNames = listOf(
                    "studio.lunabee.compose.demo.presenter.timer",
                    "studio.lunabee.compose.demo.presenter.simple",
                ),
            ),
        )
    }

    @Test
    fun resolve_module_root_package_name_falls_back_to_common_source_package_test() {
        assertEquals(
            "studio.lunabee.compose.demo",
            resolveModuleRootPackageName(
                configuredPackageName = null,
                sourcePackageNames = listOf(
                    "studio.lunabee.compose.demo.presenter.timer",
                    "studio.lunabee.compose.demo.presenter.simple",
                    "studio.lunabee.compose.demo.navigation",
                ),
            ),
        )
    }

    @Test
    fun resolve_named_qualifier_supports_typed_koin_named_test() {
        assertEquals(
            KoinQualifier.Typed(ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier")),
            resolveNamedQualifier(
                value = null,
                type = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier"),
            ),
        )
    }

    @Test
    fun resolve_named_qualifier_rejects_empty_named_qualifier_without_type_test() {
        val exception = assertFailsWith<InvalidReducerFactoryException> {
            resolveNamedQualifier(
                value = "",
                type = ClassName("kotlin", "Unit"),
            )
        }

        assertEquals("@Named qualifier must declare a non-empty String value or a type", exception.message)
    }
}

private data class FakePlatformInfo(
    override val platformName: String,
) : PlatformInfo
