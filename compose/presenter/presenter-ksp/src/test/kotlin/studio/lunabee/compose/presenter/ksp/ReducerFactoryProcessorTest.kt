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
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducerFactoryProcessorTest {
    private val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")

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
    fun should_recognize_concrete_reducer_class_test() {
        assertTrue(
            isConcreteReducerClass(
                classKind = ClassKind.CLASS,
                modifiers = emptySet(),
            ),
        )
    }

    @Test
    fun should_reject_abstract_reducer_class_test() {
        assertFalse(
            isConcreteReducerClass(
                classKind = ClassKind.CLASS,
                modifiers = setOf(Modifier.ABSTRACT),
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

    @Test
    fun should_generate_koin_module_at_finish_when_signatures_were_collected_test() {
        assertTrue(
            shouldGenerateKoinModuleAtFinish(
                generateKoinModule = true,
                signatures = listOf(
                    ValidReducerSignature(
                        packageName = "studio.lunabee.compose.demo.presenter.timer",
                        reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerReducer"),
                        uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                        navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
                        actionTypeName = actionType,
                        generatedVisibility = Visibility.Public,
                        factoryClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerReducerFactory"),
                        factoryArgsClassName = null,
                        constructorParameters = emptyList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun should_not_generate_koin_module_at_finish_without_signatures_test() {
        assertFalse(
            shouldGenerateKoinModuleAtFinish(
                generateKoinModule = true,
                signatures = emptyList(),
            ),
        )
    }
}

private data class FakePlatformInfo(
    override val platformName: String,
) : PlatformInfo
