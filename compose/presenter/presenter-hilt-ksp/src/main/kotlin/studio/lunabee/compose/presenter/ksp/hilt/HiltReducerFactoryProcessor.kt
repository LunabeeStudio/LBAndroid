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

package studio.lunabee.compose.presenter.ksp.hilt

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import studio.lunabee.compose.presenter.ksp.AnnotateFactoryOption
import studio.lunabee.compose.presenter.ksp.FactoryGenerationOwnership
import studio.lunabee.compose.presenter.ksp.FactoryOwningProcessorProvider
import studio.lunabee.compose.presenter.ksp.ReducerFactoryProcessor
import studio.lunabee.compose.presenter.ksp.booleanKspOption
import studio.lunabee.compose.presenter.ksp.factoryGenerationOwnership
import studio.lunabee.compose.presenter.ksp.factoryOwningProviderDiscovery
import studio.lunabee.compose.presenter.ksp.isOwnedBy

/**
 * Registers the processor generating reducer factories Hilt can bind through constructor injection.
 */
class HiltReducerFactoryProcessorProvider : SymbolProcessorProvider, FactoryOwningProcessorProvider {
    /**
     * Factory generation is taken over from the lbcpresenter-ksp processor by default, because a Hilt factory is
     * unusable without the `@Inject` constructor this processor adds.
     */
    override fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean = annotateFactoryOption != false

    /**
     * Creates the processor used to generate reducer factories with their Hilt annotations.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val configuredAnnotateFactory = environment.options.booleanKspOption(AnnotateFactoryOption)
        val discovery = factoryOwningProviderDiscovery()
        val ownership = factoryGenerationOwnership(annotateFactoryOption = configuredAnnotateFactory, discovery = discovery)
        val annotateFactory = ownership.isOwnedBy(this)
        reportConfiguration(
            environment = environment,
            configuredAnnotateFactory = configuredAnnotateFactory,
            ownership = ownership,
            otherProviders = discovery.providers.filterNot { it.javaClass == javaClass },
        )
        return HiltReducerFactoryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            annotateFactory = annotateFactory,
        )
    }

    private fun reportConfiguration(
        environment: SymbolProcessorEnvironment,
        configuredAnnotateFactory: Boolean?,
        ownership: FactoryGenerationOwnership,
        otherProviders: List<FactoryOwningProcessorProvider>,
    ) {
        when {
            configuredAnnotateFactory == false -> environment.logger.warn(
                "KSP option '$AnnotateFactoryOption' is disabled: reducer factories are generated without the " +
                    "'@javax.inject.Inject' constructor, so Hilt cannot bind them without a hand-written '@Provides'. " +
                    "Leave the option unset to let lbcpresenter-hilt-ksp annotate the factories.",
            )

            ownership.isOwnedBy(this) && otherProviders.isNotEmpty() -> environment.logger.warn(
                "lbcpresenter-hilt-ksp owns reducer factory generation, so the generated factories carry " +
                    "'@javax.inject.Inject' but none of the annotations of ${otherProviders.joinToString { it.javaClass.name }}. " +
                    "A Koin project relying on '@ComponentScan' must bind them through 'generatedReducerFactoryModule', or drop " +
                    "lbcpresenter-hilt-ksp from the KSP classpath.",
            )
        }
    }
}

internal class HiltReducerFactoryProcessor(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
    annotateFactory: Boolean,
) : ReducerFactoryProcessor(
    codeGenerator = codeGenerator,
    logger = logger,
    generateFactories = annotateFactory,
    factoryDecorator = HiltFactoryDecorator.takeIf { annotateFactory },
)
