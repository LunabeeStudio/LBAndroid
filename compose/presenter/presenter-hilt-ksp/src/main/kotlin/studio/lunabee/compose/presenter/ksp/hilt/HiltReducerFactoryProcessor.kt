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
import studio.lunabee.compose.presenter.ksp.FactoryOwningProcessorProvider
import studio.lunabee.compose.presenter.ksp.ReducerFactoryProcessor
import studio.lunabee.compose.presenter.ksp.factoryOwningProviders

class HiltReducerFactoryProcessorProvider : SymbolProcessorProvider, FactoryOwningProcessorProvider {
    /**
     * Factory generation is taken over from the lbcpresenter-ksp processor by default, because a Hilt factory is
     * unusable without the `@Inject` constructor this processor adds.
     */
    override fun ownsFactoryGeneration(annotateFactoryOption: Boolean?): Boolean = annotateFactoryOption != false

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val configuredAnnotateFactory = environment.options[AnnotateFactoryOption]?.toBooleanStrictOrNull()
        val otherProviders = factoryOwningProviders().filterNot { it.javaClass == javaClass }
        val clashingProviders = otherProviders.filter { it.ownsFactoryGeneration(configuredAnnotateFactory) }
        val annotateFactory = ownsFactoryGeneration(configuredAnnotateFactory) && clashingProviders.isEmpty()
        reportConfiguration(
            environment = environment,
            configuredAnnotateFactory = configuredAnnotateFactory,
            otherProviders = otherProviders,
            clashingProviders = clashingProviders,
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
        otherProviders: List<FactoryOwningProcessorProvider>,
        clashingProviders: List<FactoryOwningProcessorProvider>,
    ) {
        when {
            clashingProviders.isNotEmpty() -> environment.logger.error(
                "lbcpresenter-hilt-ksp and ${clashingProviders.joinToString { it.javaClass.name }} both take over factory " +
                    "generation with '$AnnotateFactoryOption' enabled, which would generate the same factory file twice. " +
                    "Keep a single DI processor on the KSP classpath, or leave '$AnnotateFactoryOption' unset.",
            )

            configuredAnnotateFactory == false -> environment.logger.warn(
                "KSP option '$AnnotateFactoryOption' is disabled: reducer factories are generated without the " +
                    "'@javax.inject.Inject' constructor, so Hilt cannot bind them without a hand-written '@Provides'. " +
                    "Leave the option unset to let lbcpresenter-hilt-ksp annotate the factories.",
            )

            otherProviders.isNotEmpty() -> environment.logger.warn(
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
