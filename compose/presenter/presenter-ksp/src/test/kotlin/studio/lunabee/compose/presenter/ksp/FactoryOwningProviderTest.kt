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

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryOwningProviderTest {
    private val koinLikeProvider: KoinLikeProvider = KoinLikeProvider()
    private val hiltLikeProvider: HiltLikeProvider = HiltLikeProvider()

    @Test
    fun keep_only_factory_owning_registered_providers_test() {
        val providers = factoryOwningProviders(
            providers = sequenceOf(FakeProcessorProvider(), koinLikeProvider),
        )

        assertEquals(listOf(koinLikeProvider), providers)
    }

    @Test
    fun own_factory_generation_by_default_test() {
        assertEquals(
            FactoryGenerationOwnership.Owned(hiltLikeProvider),
            ownership(annotateFactoryOption = null, providers = listOf(hiltLikeProvider)),
        )
        assertEquals(
            FactoryGenerationOwnership.Base,
            ownership(annotateFactoryOption = null, providers = listOf(koinLikeProvider)),
        )
    }

    @Test
    fun own_factory_generation_when_the_option_is_enabled_test() {
        assertEquals(
            FactoryGenerationOwnership.Owned(koinLikeProvider),
            ownership(annotateFactoryOption = true, providers = listOf(koinLikeProvider)),
        )
    }

    @Test
    fun stand_down_when_the_option_is_disabled_test() {
        assertEquals(
            FactoryGenerationOwnership.Base,
            ownership(annotateFactoryOption = false, providers = listOf(hiltLikeProvider, koinLikeProvider)),
        )
    }

    @Test
    fun stand_down_without_any_registered_provider_test() {
        assertEquals(FactoryGenerationOwnership.Base, ownership(annotateFactoryOption = null, providers = emptyList()))
    }

    @Test
    fun report_ambiguous_ownership_when_two_providers_own_generation_test() {
        assertEquals(
            FactoryGenerationOwnership.Ambiguous(listOf(hiltLikeProvider, koinLikeProvider)),
            ownership(annotateFactoryOption = true, providers = listOf(hiltLikeProvider, koinLikeProvider)),
        )
    }

    @Test
    fun report_unresolved_ownership_when_the_discovery_failed_test() {
        val ownership = factoryGenerationOwnership(
            annotateFactoryOption = null,
            discovery = FactoryOwningProviderDiscovery(
                providers = listOf(hiltLikeProvider),
                failures = listOf("java.util.ServiceConfigurationError: broken provider"),
            ),
        )

        assertEquals(
            FactoryGenerationOwnership.Unresolved(listOf("java.util.ServiceConfigurationError: broken provider")),
            ownership,
        )
        assertFalse(ownership.isOwnedBy(hiltLikeProvider))
    }

    @Test
    fun own_generation_for_another_instance_of_the_owning_provider_test() {
        val ownership = ownership(annotateFactoryOption = null, providers = listOf(HiltLikeProvider()))

        assertTrue(ownership.isOwnedBy(hiltLikeProvider))
        assertFalse(ownership.isOwnedBy(koinLikeProvider))
    }

    @Test
    fun read_a_malformed_boolean_option_as_unset_test() {
        val malformedValues = mutableListOf<String>()

        val value = mapOf(AnnotateFactoryOption to "False ").booleanKspOption(AnnotateFactoryOption) {
            malformedValues += it
        }

        assertEquals(null, value)
        assertEquals(listOf("False "), malformedValues)
    }

    @Test
    fun read_a_padded_boolean_option_test() {
        assertEquals(false, mapOf(AnnotateFactoryOption to " false ").booleanKspOption(AnnotateFactoryOption))
        assertEquals(true, mapOf(AnnotateFactoryOption to "true").booleanKspOption(AnnotateFactoryOption))
        assertEquals(null, emptyMap<String, String>().booleanKspOption(AnnotateFactoryOption))
    }

    private fun ownership(
        annotateFactoryOption: Boolean?,
        providers: List<FactoryOwningProcessorProvider>,
    ): FactoryGenerationOwnership = factoryGenerationOwnership(
        annotateFactoryOption = annotateFactoryOption,
        discovery = FactoryOwningProviderDiscovery(providers = providers),
    )
}

private open class FakeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = error("not used")
}

private class KoinLikeProvider : FakeProcessorProvider(), FactoryOwningProcessorProvider {
    override fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean = annotateFactoryOption == true
}

private class HiltLikeProvider : FakeProcessorProvider(), FactoryOwningProcessorProvider {
    override fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean = annotateFactoryOption != false
}
