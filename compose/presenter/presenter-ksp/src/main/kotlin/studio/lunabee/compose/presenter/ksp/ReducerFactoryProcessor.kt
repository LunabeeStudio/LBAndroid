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
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import studio.lunabee.compose.presenter.GenerateReducerFactory
import studio.lunabee.compose.presenter.FactoryArg

private val generateReducerFactoryAnnotation: String = checkNotNull(GenerateReducerFactory::class.qualifiedName)
private val factoryArgAnnotation: String = checkNotNull(FactoryArg::class.qualifiedName)
private const val SingleReducerQualifiedName = "studio.lunabee.compose.presenter.LBSingleReducer"

class ReducerFactoryProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates the processor used to generate reducer factories.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = ReducerFactoryProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
    )
}

internal class ReducerFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val fileGenerator: ReducerFactoryFileGenerator = ReducerFactoryFileGenerator()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(generateReducerFactoryAnnotation)
            .forEach { symbol ->
                if (!symbol.validate()) {
                    deferred += symbol
                    return@forEach
                }

                val declaration = symbol as? KSClassDeclaration
                if (declaration == null) {
                    logger.error("@GenerateReducerFactory can only target classes", symbol)
                    return@forEach
                }

                val fileSpec = buildFileSpec(declaration) ?: return@forEach
                val dependencies = declaration.containingFile?.let { Dependencies(false, it) } ?: Dependencies(false)
                codeGenerator.createNewFile(
                    dependencies = dependencies,
                    packageName = fileSpec.packageName,
                    fileName = fileSpec.name,
                ).bufferedWriter().use { writer ->
                    writer.write(fileGenerator.render(fileSpec))
                }
            }
        return deferred
    }

    private fun buildFileSpec(declaration: KSClassDeclaration) =
        runCatching {
            val signature = validator.validate(declaration.toRawSignature())
            fileGenerator.generate(signature)
        }.getOrElse { throwable ->
            logger.error(throwable.message ?: "Failed to generate reducer factory", declaration)
            null
        }

    private fun KSClassDeclaration.toRawSignature(): RawReducerSignature {
        if (classKind != ClassKind.CLASS) {
            throw InvalidReducerFactoryException("Reducer factory generation only supports concrete classes")
        }

        val primaryConstructor: KSFunctionDeclaration = primaryConstructor
            ?: throw InvalidReducerFactoryException("Reducer factory generation requires a primary constructor")

        val reducerSuperType = superTypes
            .map { it.resolve() }
            .firstOrNull { it.declaration.qualifiedName?.asString() == SingleReducerQualifiedName }
            ?: throw InvalidReducerFactoryException("Annotated reducers must directly extend LBSingleReducer")

        val reducerTypeArguments = reducerSuperType.arguments.mapNotNull { it.type?.resolve() }
        if (reducerTypeArguments.size != 3) {
            throw InvalidReducerFactoryException("Unable to resolve reducer generic arguments")
        }

        return RawReducerSignature(
            packageName = packageName.asString(),
            reducerClassName = toClassName(),
            uiStateTypeName = reducerTypeArguments[0].toTypeName(),
            navScopeTypeName = reducerTypeArguments[1].toTypeName(),
            actionTypeName = reducerTypeArguments[2].toTypeName(),
            constructorVisibility = primaryConstructor.toVisibility(),
            constructorParameters = primaryConstructor.parameters.map { parameter ->
                RawReducerParameter(
                    name = parameter.name?.asString()
                        ?: throw InvalidReducerFactoryException("Reducer constructor parameters must be named"),
                    typeName = parameter.type.toTypeName(),
                    hasRuntimeAnnotation = parameter.annotations.any {
                        it.annotationType.resolve().declaration.qualifiedName?.asString() == factoryArgAnnotation
                    },
                    hasDefault = parameter.hasDefault,
                    isVararg = parameter.isVararg,
                )
            },
        )
    }

    private fun KSFunctionDeclaration.toVisibility(): Visibility = when {
        Modifier.PRIVATE in modifiers -> Visibility.Private
        Modifier.PROTECTED in modifiers -> Visibility.Protected
        Modifier.INTERNAL in modifiers -> Visibility.Internal
        else -> Visibility.Public
    }
}
