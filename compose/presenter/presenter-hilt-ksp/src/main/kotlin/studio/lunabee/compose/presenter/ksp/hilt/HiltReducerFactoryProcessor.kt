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
import studio.lunabee.compose.presenter.ksp.ReducerFactoryProcessor

private const val AnnotateFactoryOption = "studio.lunabee.presenter.annotateFactory"

class HiltReducerFactoryProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates the processor generating Hilt ready reducer factories.
     *
     * Factory generation is taken over from the lbcpresenter-ksp processor by default, because a Hilt factory is
     * unusable without the `@Inject` constructor this processor adds. Set the KSP option
     * `studio.lunabee.presenter.annotateFactory` to false to fall back to the undecorated factories.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val configuredAnnotateFactory = environment.options[AnnotateFactoryOption]?.toBooleanStrictOrNull()
        val annotateFactory = configuredAnnotateFactory ?: true
        // Koin only takes over factory generation when the option is explicitly enabled, so this is the single
        // configuration where both DI processors would emit the same factory file.
        if (configuredAnnotateFactory == true && isKoinProcessorOnClasspath()) {
            environment.logger.error(
                "lbcpresenter-hilt-ksp and lbcpresenter-koin-ksp both take over factory generation when " +
                    "'$AnnotateFactoryOption' is enabled, which would generate the same factory file twice. " +
                    "Keep a single DI processor on the KSP classpath, or leave '$AnnotateFactoryOption' unset.",
            )
        }
        return HiltReducerFactoryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            annotateFactory = annotateFactory,
        )
    }
}

/**
 * Generates the reducer factories of the lbcpresenter-ksp processor with the Hilt constructor injection annotations
 * added by [HiltFactoryDecorator].
 */
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

private fun isKoinProcessorOnClasspath(): Boolean =
    runCatching {
        Class.forName(
            "studio.lunabee.compose.presenter.ksp.koin.KoinReducerFactoryProcessorProvider",
            false,
            HiltReducerFactoryProcessorProvider::class.java.classLoader,
        )
    }.isSuccess
