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

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec

private const val GenerateKoinModuleOption = "studio.lunabee.presenter.generateKoinModule"
private const val AnnotateFactoryOption = "studio.lunabee.presenter.annotateFactory"

class ReducerFactoryProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates the processor used to generate reducer factories.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val annotateFactoryRequested = environment.options[AnnotateFactoryOption]?.toBooleanStrictOrNull() == true
        if (annotateFactoryRequested) {
            environment.logger.warn(
                "KSP option '$AnnotateFactoryOption' is enabled: factory generation is delegated to the lbcpresenter-koin-ksp " +
                    "processor. Make sure lbcpresenter-koin-ksp is on the KSP classpath.",
            )
        }
        if (environment.options[GenerateKoinModuleOption]?.toBooleanStrictOrNull() == true) {
            environment.logger.warn(
                "KSP option '$GenerateKoinModuleOption' is handled by the lbcpresenter-koin-ksp processor. " +
                    "Make sure lbcpresenter-koin-ksp is on the KSP classpath.",
            )
        }
        return ReducerFactoryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            generateFactories = !annotateFactoryRequested,
        )
    }
}

/**
 * Generates a reducer factory for every class annotated with
 * [studio.lunabee.compose.presenter.GenerateReducerFactory].
 *
 * DI specific processors (e.g. lbcpresenter-koin-ksp) can extend this processor to decorate the generated factories and
 * produce extra aggregating files through [onProcessStart], [onProcessEnd] and [SymbolProcessor.finish].
 */
open class ReducerFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    /**
     * Logger usable by subclasses to report DI specific diagnostics.
     */
    protected val logger: KSPLogger,
    private val generateFactories: Boolean = true,
    factoryDecorator: GeneratedFactoryDecorator? = null,
) : SymbolProcessor {
    /**
     * Parser shared with subclasses to extract reducer signatures from declarations.
     */
    protected val parser: ReducerSignatureParser = ReducerSignatureParser()
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val fileGenerator: ReducerFactoryFileGenerator = ReducerFactoryFileGenerator(decorator = factoryDecorator)

    final override fun process(resolver: Resolver): List<KSAnnotated> {
        onProcessStart(resolver)
        val deferred = mutableListOf<KSAnnotated>()
        if (generateFactories) {
            resolver.getSymbolsWithAnnotation(GenerateReducerFactoryAnnotationName)
                .forEach { symbol -> processSymbol(symbol, deferred) }
        }
        onProcessEnd(resolver, deferred)

        return deferred.distinct()
    }

    /**
     * Called at the beginning of every processing round, before factory generation.
     */
    protected open fun onProcessStart(resolver: Resolver) {
        // no-op by default
    }

    /**
     * Called at the end of every processing round, after factory generation. Subclasses can append symbols to [deferred]
     * to postpone them to the next round.
     */
    protected open fun onProcessEnd(
        resolver: Resolver,
        deferred: MutableList<KSAnnotated>,
    ) {
        // no-op by default
    }

    /**
     * Parses and validates the reducer signature of [declaration], reporting a KSP error and returning null on failure.
     */
    protected fun buildValidSignature(declaration: KSClassDeclaration): ValidReducerSignature? =
        runCatching {
            validator.validate(parser.parse(declaration))
        }.getOrElse { throwable ->
            logger.error(throwable.message ?: "Failed to generate reducer factory", declaration)
            null
        }

    /**
     * Writes [fileSpec] through the KSP code generator with the given incremental [dependencies].
     */
    protected fun writeGeneratedFile(
        fileSpec: FileSpec,
        dependencies: Dependencies,
    ) {
        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = fileSpec.packageName,
            fileName = fileSpec.name,
        ).bufferedWriter().use { writer ->
            writer.write(fileGenerator.render(fileSpec))
        }
    }

    private fun processSymbol(
        symbol: KSAnnotated,
        deferred: MutableList<KSAnnotated>,
    ) {
        if (!symbol.validate()) {
            logger.warn("Symbol not validate", symbol)
            deferred += symbol
            return
        }

        val declaration = symbol as? KSClassDeclaration
        if (declaration == null) {
            logger.error("@GenerateReducerFactory can only target classes", symbol)
            return
        }

        val signature = buildValidSignature(declaration) ?: return
        logger.info("Reducer factory generated", symbol)
        writeGeneratedFile(
            fileSpec = fileGenerator.generate(signature),
            dependenciesFile = declaration.containingFile,
        )
    }

    private fun writeGeneratedFile(
        fileSpec: FileSpec,
        dependenciesFile: KSFile?,
    ) {
        val dependencies = dependenciesFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        writeGeneratedFile(fileSpec, dependencies)
    }
}
