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
import kotlin.test.assertNull
import kotlin.test.assertSame

class FactoryOwningProviderTest {
    private val koinLikeProvider: FakeFactoryOwningProvider = FakeFactoryOwningProvider { it == true }
    private val hiltLikeProvider: FakeFactoryOwningProvider = FakeFactoryOwningProvider { it != false }

    @Test
    fun keep_only_factory_owning_registered_providers_test() {
        val providers = factoryOwningProviders(
            providers = sequenceOf(FakeProcessorProvider(), koinLikeProvider),
        )

        assertEquals(listOf(koinLikeProvider), providers)
    }

    @Test
    fun own_factory_generation_by_default_test() {
        assertSame(hiltLikeProvider, factoryGenerationOwner(annotateFactoryOption = null, providers = listOf(hiltLikeProvider)))
        assertNull(factoryGenerationOwner(annotateFactoryOption = null, providers = listOf(koinLikeProvider)))
    }

    @Test
    fun own_factory_generation_when_the_option_is_enabled_test() {
        assertSame(koinLikeProvider, factoryGenerationOwner(annotateFactoryOption = true, providers = listOf(koinLikeProvider)))
    }

    @Test
    fun stand_down_when_the_option_is_disabled_test() {
        assertNull(factoryGenerationOwner(annotateFactoryOption = false, providers = listOf(hiltLikeProvider, koinLikeProvider)))
    }

    @Test
    fun stand_down_without_any_registered_provider_test() {
        assertNull(factoryGenerationOwner(annotateFactoryOption = null, providers = emptyList()))
    }
}

private open class FakeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = error("not used")
}

private class FakeFactoryOwningProvider(
    private val owns: (Boolean?) -> Boolean,
) : FakeProcessorProvider(), FactoryOwningProcessorProvider {
    override fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean = owns(annotateFactoryOption)
}
