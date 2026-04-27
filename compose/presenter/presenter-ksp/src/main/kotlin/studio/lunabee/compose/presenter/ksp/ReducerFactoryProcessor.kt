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
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import studio.lunabee.compose.presenter.FactoryArg
import studio.lunabee.compose.presenter.GenerateReducerFactory

private val generateReducerFactoryAnnotation: String = checkNotNull(GenerateReducerFactory::class.qualifiedName)
internal val factoryArgAnnotations: Set<String> = setOf(
    checkNotNull(FactoryArg::class.qualifiedName),
    "org.koin.core.annotation.InjectedParam",
    "dagger.assisted.Assisted",
)
private val namedQualifierAnnotations: Set<String> = setOf(
    "jakarta.inject.Named",
    "javax.inject.Named",
    "org.koin.core.annotation.Named",
)
private val qualifierMetaAnnotations: Set<String> = setOf(
    "jakarta.inject.Qualifier",
    "javax.inject.Qualifier",
    "org.koin.core.annotation.Qualifier",
)
private val kotlinUnitClassName: ClassName = ClassName("kotlin", "Unit")
private const val SingleReducerQualifiedName = "studio.lunabee.compose.presenter.LBSingleReducer"
private const val GenerateKoinModuleOption = "studio.lunabee.presenter.generateKoinModule"
private const val KoinModulePackageOption = "studio.lunabee.presenter.koinModulePackage"
private const val UseKoinAnnotationsOption = "studio.lunabee.presenter.useKoinAnnotations"

class ReducerFactoryProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates the processor used to generate reducer factories.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val useKoinAnnotations = environment.options[UseKoinAnnotationsOption]?.toBooleanStrictOrNull() == true
        val koinModuleGenerationRequested = environment.options[GenerateKoinModuleOption]?.toBooleanStrictOrNull() == true
        if (useKoinAnnotations && koinModuleGenerationRequested) {
            environment.logger.warn(
                "KSP options '$GenerateKoinModuleOption' and '$UseKoinAnnotationsOption' are both set; " +
                    "annotations mode takes precedence and the aggregated Koin module will not be generated.",
            )
        }
        val generateKoinModule = !useKoinAnnotations &&
            koinModuleGenerationRequested &&
            shouldGenerateKoinModuleForCompilation(environment.platforms)
        if (!useKoinAnnotations && koinModuleGenerationRequested && !generateKoinModule) {
            environment.logger.info(
                "Skipping reducer Koin module generation for this compilation because it cannot aggregate all factories. " +
                    "A platform compilation will generate the shared module instead.",
            )
        }
        return ReducerFactoryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            generateKoinModule = generateKoinModule,
            configuredKoinModulePackageName = environment.options[KoinModulePackageOption]?.trim()?.takeIf { it.isNotEmpty() },
            useKoinAnnotations = useKoinAnnotations,
        )
    }
}

internal fun shouldGenerateKoinModuleForCompilation(platforms: List<PlatformInfo>): Boolean = platforms.size == 1

internal class ReducerFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val generateKoinModule: Boolean,
    private val configuredKoinModulePackageName: String?,
    private val useKoinAnnotations: Boolean = false,
) : SymbolProcessor {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val fileGenerator: ReducerFactoryFileGenerator = ReducerFactoryFileGenerator()
    private val koinModuleSignatures: LinkedHashMap<String, ValidReducerSignature> = linkedMapOf()
    private val moduleSourcePackageNames: LinkedHashSet<String> = linkedSetOf()
    private var moduleRootPackageName: String? = null
    private var hasWarnedAboutMissingKoinModulePackageOption: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        collectKoinModuleContext(resolver)
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(generateReducerFactoryAnnotation)
            .forEach { symbol -> processSymbol(symbol, deferred) }
        collectKoinModuleSignatures(resolver, deferred)

        return deferred.distinct()
    }

    override fun finish() {
        if (!shouldGenerateKoinModuleAtFinish(generateKoinModule, koinModuleSignatures.values)) return

        val fileSpec = fileGenerator.generateKoinModule(
            signatures = koinModuleSignatures.values.toList(),
            moduleRootPackageName = moduleRootPackageName
                ?: commonPackageName(koinModuleSignatures.values.map { it.packageName }),
        )
        writeGeneratedAggregatingFile(fileSpec)
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

        val generatedFactoryFiles = buildFileSpec(declaration) ?: return
        logger.info("Reducer factory generated", symbol)
        writeGeneratedFile(generatedFactoryFiles.fileSpec, declaration.containingFile)
    }

    private fun collectKoinModuleContext(resolver: Resolver) {
        if (!generateKoinModule) return

        moduleSourcePackageNames += resolver.getAllFiles().map { it.packageName.asString() }.toList()
        moduleRootPackageName = resolveModuleRootPackageName(
            configuredPackageName = configuredKoinModulePackageName,
            sourcePackageNames = moduleSourcePackageNames.toList(),
        )
        warnAboutMissingKoinModulePackageOptionIfNeeded()
    }

    private fun warnAboutMissingKoinModulePackageOptionIfNeeded() {
        if (configuredKoinModulePackageName != null) return
        if (hasWarnedAboutMissingKoinModulePackageOption) return
        logger.warn(
            "KSP option '$KoinModulePackageOption' is not set. Falling back to package inference from source files for " +
                "generatedReducerFactoryModule. Add the option to make the generated Koin module package explicit and stable.",
        )
        hasWarnedAboutMissingKoinModulePackageOption = true
    }

    private fun collectKoinModuleSignatures(
        resolver: Resolver,
        deferred: MutableList<KSAnnotated>,
    ) {
        if (!generateKoinModule) return

        koinModuleSignatures.clear()
        // KSP incremental runs may not surface every annotated reducer in getSymbolsWithAnnotation().
        // Rebuilding the aggregating module from all visible source files keeps the shared Koin module stable.
        resolver.getAllFiles()
            .asSequence()
            .flatMap { file -> file.annotatedReducerDeclarations() }
            .forEach { declaration ->
                if (!declaration.validate()) {
                    deferred += declaration
                    return@forEach
                }

                val signature = buildValidSignature(declaration) ?: return@forEach
                koinModuleSignatures[signature.factoryClassName.canonicalName] = signature
            }
    }

    private fun writeGeneratedFile(
        fileSpec: com.squareup.kotlinpoet.FileSpec,
        dependenciesFile: KSFile?,
    ) {
        val dependencies = dependenciesFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        writeGeneratedFile(fileSpec, dependencies)
    }

    private fun writeGeneratedAggregatingFile(fileSpec: com.squareup.kotlinpoet.FileSpec) {
        // The shared Koin module needs full-source invalidation so KSP does not drop it on unrelated incremental reruns.
        writeGeneratedFile(fileSpec, Dependencies.ALL_FILES)
    }

    private fun writeGeneratedFile(
        fileSpec: com.squareup.kotlinpoet.FileSpec,
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

    private fun buildFileSpec(declaration: KSClassDeclaration) =
        runCatching {
            val signature = validator.validate(declaration.toRawSignature())
            GeneratedReducerFactoryFiles(
                signature = signature,
                fileSpec = fileGenerator.generate(signature = signature, useKoinAnnotations = useKoinAnnotations),
            )
        }.getOrElse { throwable ->
            logger.error(throwable.message ?: "Failed to generate reducer factory", declaration)
            null
        }

    private fun buildValidSignature(declaration: KSClassDeclaration): ValidReducerSignature? =
        runCatching {
            validator.validate(declaration.toRawSignature())
        }.getOrElse { throwable ->
            logger.error(throwable.message ?: "Failed to generate reducer factory", declaration)
            null
        }

    private fun KSClassDeclaration.toRawSignature(): RawReducerSignature {
        if (!isConcreteReducerClass(classKind = classKind, modifiers = modifiers)) {
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
            reducerVisibility = toVisibility(),
            constructorVisibility = primaryConstructor.toVisibility(),
            constructorParameters = primaryConstructor.parameters.map { parameter ->
                RawReducerParameter(
                    name = parameter.name?.asString()
                        ?: throw InvalidReducerFactoryException("Reducer constructor parameters must be named"),
                    typeName = parameter.type.toTypeName(),
                    hasRuntimeAnnotation = parameter.annotations.any {
                        it.annotationType.resolve().declaration.qualifiedName?.asString() in factoryArgAnnotations
                    },
                    hasDefault = parameter.hasDefault,
                    isVararg = parameter.isVararg,
                    qualifier = parameter.toKoinQualifier(),
                )
            },
        )
    }

    private fun KSDeclaration.toVisibility(): Visibility = when {
        Modifier.PRIVATE in modifiers -> Visibility.Private
        Modifier.PROTECTED in modifiers -> Visibility.Protected
        Modifier.INTERNAL in modifiers -> Visibility.Internal
        else -> Visibility.Public
    }

    private fun KSValueParameter.toKoinQualifier(): KoinQualifier? {
        val qualifiers = annotations.mapNotNull { it.toKoinQualifier() }.toList()
        if (qualifiers.size > 1) {
            throw InvalidReducerFactoryException("Reducer constructor parameters support at most one qualifier annotation")
        }
        return qualifiers.singleOrNull()
    }

    private fun KSAnnotation.toKoinQualifier(): KoinQualifier? {
        val annotationDeclaration = annotationType.resolve().declaration as? KSClassDeclaration ?: return null
        val annotationQualifiedName = annotationDeclaration.qualifiedName?.asString() ?: return null
        if (annotationQualifiedName in factoryArgAnnotations) return null

        if (annotationQualifiedName in namedQualifierAnnotations) {
            return resolveNamedQualifier(
                value = stringArgumentValue(),
                type = classArgumentValue(argumentName = "type"),
            )
        }

        return if (annotationDeclaration.isQualifierAnnotation()) {
            KoinQualifier.Typed(annotationDeclaration.toClassName())
        } else {
            null
        }
    }

    private fun KSClassDeclaration.isQualifierAnnotation(): Boolean =
        annotations.any { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() in qualifierMetaAnnotations
        }

    private fun KSAnnotation.stringArgumentValue(): String? =
        arguments.firstOrNull { argument ->
            argument.name?.asString() == "value" || argument.name == null
        }?.value as? String

    private fun KSAnnotation.classArgumentValue(argumentName: String): ClassName? {
        val value = arguments.firstOrNull { argument -> argument.name?.asString() == argumentName }?.value as? KSType
        val declaration = value?.declaration as? KSClassDeclaration ?: return null
        return declaration.toClassName()
    }

    private fun KSFile.annotatedReducerDeclarations(): Sequence<KSClassDeclaration> =
        declarations.asSequence().flatMap { declaration -> declaration.annotatedReducerDeclarations() }

    private fun KSDeclaration.annotatedReducerDeclarations(): Sequence<KSClassDeclaration> = sequence {
        val classDeclaration = this@annotatedReducerDeclarations as? KSClassDeclaration ?: return@sequence
        if (classDeclaration.hasGenerateReducerFactoryAnnotation()) {
            yield(classDeclaration)
        }
        classDeclaration.declarations.forEach { declaration ->
            yieldAll(declaration.annotatedReducerDeclarations())
        }
    }

    private fun KSClassDeclaration.hasGenerateReducerFactoryAnnotation(): Boolean =
        annotations.any { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == generateReducerFactoryAnnotation
        }
}

internal fun resolveNamedQualifier(
    value: String?,
    type: ClassName?,
): KoinQualifier {
    value?.takeIf { it.isNotEmpty() }?.let { qualifierName ->
        return KoinQualifier.Named(qualifierName)
    }
    type?.takeUnless { it == kotlinUnitClassName }?.let { qualifierType ->
        return KoinQualifier.Typed(qualifierType)
    }
    throw InvalidReducerFactoryException("@Named qualifier must declare a non-empty String value or a type")
}

internal fun shouldGenerateKoinModuleAtFinish(
    generateKoinModule: Boolean,
    signatures: Collection<ValidReducerSignature>,
): Boolean = generateKoinModule && signatures.isNotEmpty()

internal fun isConcreteReducerClass(
    classKind: ClassKind,
    modifiers: Set<Modifier>,
): Boolean = classKind == ClassKind.CLASS && Modifier.ABSTRACT !in modifiers

internal fun resolveModuleRootPackageName(
    configuredPackageName: String?,
    sourcePackageNames: List<String>,
): String {
    configuredPackageName?.takeIf { it.isNotBlank() }?.let { return it }

    val normalizedSourcePackageNames = sourcePackageNames.filter { it.isNotBlank() }
    return normalizedSourcePackageNames.firstOrNull()?.let { commonPackageName(normalizedSourcePackageNames) } ?: ""
}
