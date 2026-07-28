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

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import studio.lunabee.compose.presenter.FactoryArg
import studio.lunabee.compose.presenter.GenerateReducerFactory

/**
 * Fully qualified name of the annotation triggering reducer factory generation.
 */
val GenerateReducerFactoryAnnotationName: String = checkNotNull(GenerateReducerFactory::class.qualifiedName)

private val factoryArgAnnotation: String = checkNotNull(FactoryArg::class.qualifiedName)
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
private val providedAnnotations: Set<String> = setOf(
    "org.koin.core.annotation.Provided",
)
private val kotlinUnitClassName: ClassName = ClassName("kotlin", "Unit")
private const val SingleReducerQualifiedName = "studio.lunabee.compose.presenter.LBSingleReducer"

/**
 * Extracts [RawReducerSignature] from reducer declarations annotated with
 * [studio.lunabee.compose.presenter.GenerateReducerFactory].
 */
class ReducerSignatureParser {
    /**
     * Builds the [RawReducerSignature] of the annotated reducer [declaration].
     *
     * @throws InvalidReducerFactoryException when the declaration cannot be turned into a reducer signature
     */
    fun parse(declaration: KSClassDeclaration): RawReducerSignature {
        if (!isConcreteReducerClass(classKind = declaration.classKind, modifiers = declaration.modifiers)) {
            throw InvalidReducerFactoryException("Reducer factory generation only supports concrete classes")
        }

        val primaryConstructor: KSFunctionDeclaration = declaration.primaryConstructor
            ?: throw InvalidReducerFactoryException("Reducer factory generation requires a primary constructor")

        val reducerSuperType = declaration.superTypes
            .map { it.resolve() }
            .firstOrNull { it.declaration.qualifiedName?.asString() == SingleReducerQualifiedName }
            ?: throw InvalidReducerFactoryException("Annotated reducers must directly extend LBSingleReducer")

        val reducerTypeArguments = reducerSuperType.arguments.mapNotNull { it.type?.resolve() }
        if (reducerTypeArguments.size != 3) {
            throw InvalidReducerFactoryException("Unable to resolve reducer generic arguments")
        }

        return RawReducerSignature(
            packageName = declaration.packageName.asString(),
            reducerClassName = declaration.toClassName(),
            uiStateTypeName = reducerTypeArguments[0].toTypeName(),
            navScopeTypeName = reducerTypeArguments[1].toTypeName(),
            actionTypeName = reducerTypeArguments[2].toTypeName(),
            reducerVisibility = declaration.toVisibility(),
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
                    qualifier = parameter.toDiQualifier(),
                    isProvided = parameter.isProvided(),
                )
            },
        )
    }

    /**
     * Returns all reducer declarations annotated with
     * [studio.lunabee.compose.presenter.GenerateReducerFactory] declared in [file], including nested classes.
     */
    fun annotatedReducerDeclarations(file: KSFile): Sequence<KSClassDeclaration> =
        file.declarations.asSequence().flatMap { declaration -> declaration.annotatedReducerDeclarations() }

    private fun KSDeclaration.toVisibility(): Visibility = when {
        Modifier.PRIVATE in modifiers -> Visibility.Private
        Modifier.PROTECTED in modifiers -> Visibility.Protected
        Modifier.INTERNAL in modifiers -> Visibility.Internal
        else -> Visibility.Public
    }

    private fun KSValueParameter.isProvided(): Boolean =
        annotations.any { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() in providedAnnotations
        }

    private fun KSValueParameter.toDiQualifier(): DiQualifier? {
        val qualifiers = annotations.mapNotNull { it.toDiQualifier() }.toList()
        if (qualifiers.size > 1) {
            throw InvalidReducerFactoryException("Reducer constructor parameters support at most one qualifier annotation")
        }
        return qualifiers.singleOrNull()
    }

    private fun KSAnnotation.toDiQualifier(): DiQualifier? {
        val annotationDeclaration = annotationType.resolve().declaration as? KSClassDeclaration ?: return null
        val annotationQualifiedName = annotationDeclaration.qualifiedName?.asString() ?: return null
        if (annotationQualifiedName == factoryArgAnnotation) return null

        if (annotationQualifiedName in namedQualifierAnnotations) {
            return resolveNamedQualifier(
                value = stringArgumentValue(),
                type = classArgumentValue(argumentName = "type"),
            )
        }

        return if (annotationDeclaration.isQualifierAnnotation()) {
            DiQualifier.Typed(annotationDeclaration.toClassName())
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
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == GenerateReducerFactoryAnnotationName
        }
}

internal fun resolveNamedQualifier(
    value: String?,
    type: ClassName?,
): DiQualifier {
    value?.takeIf { it.isNotEmpty() }?.let { qualifierName ->
        return DiQualifier.Named(qualifierName)
    }
    type?.takeUnless { it == kotlinUnitClassName }?.let { qualifierType ->
        return DiQualifier.Typed(qualifierType)
    }
    throw InvalidReducerFactoryException("@Named qualifier must declare a non-empty String value or a type")
}

internal fun isConcreteReducerClass(
    classKind: ClassKind,
    modifiers: Set<Modifier>,
): Boolean = classKind == ClassKind.CLASS && Modifier.ABSTRACT !in modifiers
