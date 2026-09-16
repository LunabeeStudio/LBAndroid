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

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.util.ServiceLoader

/**
 * KSP option asking a DI specific processor to generate the reducer factories itself, with its DI annotations.
 */
const val AnnotateFactoryOption: String = "studio.lunabee.presenter.annotateFactory"

/**
 * Implemented by the [SymbolProcessorProvider] of a DI specific processor able to generate the reducer factories
 * itself. [ReducerFactoryProcessor] stands down as soon as one of them owns generation for the current configuration,
 * so a factory file is never written twice.
 */
interface FactoryOwningProcessorProvider {
    /**
     * True when this provider generates the factories for the given [AnnotateFactoryOption] value, null meaning the
     * option is unset.
     */
    fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean
}

/**
 * Factory owning providers registered as KSP services on the processor classpath. All KSP processors of a compilation
 * share the same classloader, so a provider found here runs in the same rounds as the caller.
 */
fun factoryOwningProviders(
    providers: Sequence<SymbolProcessorProvider> = registeredProcessorProviders(),
): List<FactoryOwningProcessorProvider> = providers.filterIsInstance<FactoryOwningProcessorProvider>().toList()

internal fun factoryGenerationOwner(
    annotateFactoryOption: Boolean?,
    providers: List<FactoryOwningProcessorProvider> = factoryOwningProviders(),
): FactoryOwningProcessorProvider? = providers.firstOrNull { it.ownsFactoryGeneration(annotateFactoryOption) }

private fun registeredProcessorProviders(): Sequence<SymbolProcessorProvider> =
    runCatching {
        ServiceLoader.load(
            SymbolProcessorProvider::class.java,
            ReducerFactoryProcessorProvider::class.java.classLoader,
        ).toList()
    }.getOrDefault(emptyList()).asSequence()
