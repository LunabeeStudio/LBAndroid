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
 * Implemented by the [SymbolProcessorProvider] of a DI specific processor able to generate the reducer factories
 * itself. Every processor of the compilation resolves ownership with [factoryGenerationOwnership] and generates the
 * factories only when it owns them, so a factory file is never written twice.
 */
interface FactoryOwningProcessorProvider {
    /**
     * True when this provider generates the factories for the given [AnnotateFactoryOption] value, null meaning the
     * option is unset.
     */
    fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean
}

/**
 * Factory owning providers registered as KSP services on the processor classpath, and the [failures] met while loading
 * them. All KSP processors of a compilation share the same classloader, so a provider found here runs in the same
 * rounds as the caller.
 */
class FactoryOwningProviderDiscovery(
    val providers: List<FactoryOwningProcessorProvider>,
    val failures: List<String> = emptyList(),
)

/**
 * Which processor generates the reducer factories for the current configuration. Resolved once by
 * [factoryGenerationOwnership] and compared against each provider with [isOwnedBy], so every processor of the
 * compilation reads the same decision.
 */
sealed interface FactoryGenerationOwnership {
    /**
     * No DI specific processor owns generation: [ReducerFactoryProcessor] generates the factories itself.
     */
    data object Base : FactoryGenerationOwnership

    /**
     * [owner] is the only DI specific processor owning generation.
     */
    data class Owned(
        val owner: FactoryOwningProcessorProvider,
    ) : FactoryGenerationOwnership

    /**
     * [claimants] all own generation for the current configuration, which no processor can resolve on its own.
     */
    data class Ambiguous(
        val claimants: List<FactoryOwningProcessorProvider>,
    ) : FactoryGenerationOwnership

    /**
     * The KSP services of the processor classpath could not be read, so ownership is unknown. [failures] describes
     * what went wrong.
     */
    data class Unresolved(
        val failures: List<String>,
    ) : FactoryGenerationOwnership
}

/**
 * True when [provider] is the single owner of factory generation. Providers are compared by type, because the
 * instance discovered as a KSP service is not the instance KSP created for the compilation.
 */
fun FactoryGenerationOwnership.isOwnedBy(provider: FactoryOwningProcessorProvider): Boolean =
    this is FactoryGenerationOwnership.Owned && owner.javaClass == provider.javaClass

/**
 * Resolves the single processor generating the reducer factories for the given [annotateFactoryOption] value among
 * [discovery].
 */
fun factoryGenerationOwnership(
    annotateFactoryOption: Boolean?,
    discovery: FactoryOwningProviderDiscovery,
): FactoryGenerationOwnership {
    if (discovery.failures.isNotEmpty()) {
        return FactoryGenerationOwnership.Unresolved(discovery.failures)
    }
    val claimants = discovery.providers.filter { it.ownsFactoryGeneration(annotateFactoryOption) }
    return when (claimants.size) {
        0 -> FactoryGenerationOwnership.Base
        1 -> FactoryGenerationOwnership.Owned(claimants.single())
        else -> FactoryGenerationOwnership.Ambiguous(claimants)
    }
}

/**
 * Factory owning providers registered as KSP services, loaded once per processor classloader.
 */
fun factoryOwningProviderDiscovery(): FactoryOwningProviderDiscovery = discoveredFactoryOwningProviders

/**
 * Keeps the factory owning providers of [providers], whatever the DI framework they belong to.
 */
fun factoryOwningProviders(providers: Sequence<SymbolProcessorProvider>): List<FactoryOwningProcessorProvider> =
    providers.filterIsInstance<FactoryOwningProcessorProvider>().toList()

private val discoveredFactoryOwningProviders: FactoryOwningProviderDiscovery by lazy { loadFactoryOwningProviders() }

private fun loadFactoryOwningProviders(): FactoryOwningProviderDiscovery {
    val failures = mutableListOf<String>()
    val registeredProviders = runCatching {
        ServiceLoader.load(
            SymbolProcessorProvider::class.java,
            ReducerFactoryProcessorProvider::class.java.classLoader,
        ).stream().toList()
    }.getOrElse { failure ->
        failures += failure.describeDiscoveryFailure()
        emptyList()
    }
    val providers = registeredProviders.mapNotNull { registeredProvider ->
        runCatching { registeredProvider.get() }.getOrElse { failure ->
            failures += failure.describeDiscoveryFailure()
            null
        }
    }
    return FactoryOwningProviderDiscovery(
        providers = factoryOwningProviders(providers.asSequence()),
        failures = failures,
    )
}

private fun Throwable.describeDiscoveryFailure(): String = "${javaClass.name}: ${message.orEmpty()}"
