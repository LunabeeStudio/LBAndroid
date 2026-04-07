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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

private val coroutineScopeType: ClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
private val kotlinUnitType: ClassName = ClassName("kotlin", "Unit")

internal data class RawReducerSignature(
    val packageName: String,
    val reducerClassName: ClassName,
    val uiStateTypeName: TypeName,
    val navScopeTypeName: TypeName,
    val actionTypeName: TypeName,
    val reducerVisibility: Visibility = Visibility.Public,
    val constructorVisibility: Visibility,
    val constructorParameters: List<RawReducerParameter>,
)

internal data class RawReducerParameter(
    val name: String,
    val typeName: TypeName,
    val hasRuntimeAnnotation: Boolean,
    val hasDefault: Boolean,
    val isVararg: Boolean,
    val qualifier: KoinQualifier? = null,
)

internal data class ValidReducerSignature(
    val packageName: String,
    val reducerClassName: ClassName,
    val uiStateTypeName: TypeName,
    val navScopeTypeName: TypeName,
    val actionTypeName: TypeName,
    val generatedVisibility: Visibility,
    val factoryClassName: ClassName,
    val factoryArgsClassName: ClassName?,
    val constructorParameters: List<ValidatedReducerParameter>,
) {
    val injectedParameters: List<ValidatedReducerParameter> = constructorParameters.filter { it.kind == ParameterKind.Injected }
    val factoryArgParameters: List<ValidatedReducerParameter> = constructorParameters.filter { it.kind == ParameterKind.FactoryArg }
}

internal data class GeneratedReducerFactoryFiles(
    val signature: ValidReducerSignature,
    val fileSpec: com.squareup.kotlinpoet.FileSpec,
)

internal data class ValidatedReducerParameter(
    val name: String,
    val typeName: TypeName,
    val kind: ParameterKind,
    val qualifier: KoinQualifier? = null,
)

internal sealed interface KoinQualifier {
    data class Named(
        val value: String,
    ) : KoinQualifier

    data class Typed(
        val annotationClassName: ClassName,
    ) : KoinQualifier
}

internal enum class ParameterKind {
    CoroutineScope,
    EmitUserAction,
    Injected,
    FactoryArg,
}

internal enum class Visibility {
    Public,
    Internal,
    Protected,
    Private,
}

internal class InvalidReducerFactoryException(
    override val message: String,
) : IllegalArgumentException(message)

internal class ReducerFactorySignatureValidator {
    fun validate(signature: RawReducerSignature): ValidReducerSignature {
        ensureValid(
            condition = signature.reducerVisibility == Visibility.Public || signature.reducerVisibility == Visibility.Internal,
            message = "Reducer factory generation only supports public or internal reducers",
        )
        ensureValid(
            condition = signature.constructorVisibility != Visibility.Private &&
                signature.constructorVisibility != Visibility.Protected,
            message = "Reducer factory generation only supports public or internal constructors",
        )

        val factoryClassName = ClassName(signature.packageName, "${signature.reducerClassName.simpleName}Factory")
        val factoryArgsClassName = ClassName(signature.packageName, "${signature.reducerClassName.simpleName}FactoryArgs")
        val validatedParameters = signature.constructorParameters.map { parameter ->
            validateParameter(
                parameter = parameter,
                actionTypeName = signature.actionTypeName,
            )
        }

        val coroutineScopeCount = validatedParameters.count { it.kind == ParameterKind.CoroutineScope }
        ensureValid(
            condition = coroutineScopeCount == 1,
            message = "Reducer constructor must declare exactly one coroutineScope: CoroutineScope parameter",
        )

        val emitUserActionCount = validatedParameters.count { it.kind == ParameterKind.EmitUserAction }
        ensureValid(
            condition = emitUserActionCount == 1,
            message = "Reducer constructor must declare exactly one emitUserAction: (Action) -> Unit parameter",
        )

        return ValidReducerSignature(
            packageName = signature.packageName,
            reducerClassName = signature.reducerClassName,
            uiStateTypeName = signature.uiStateTypeName,
            navScopeTypeName = signature.navScopeTypeName,
            actionTypeName = signature.actionTypeName,
            generatedVisibility = moreRestrictiveVisibility(
                first = signature.reducerVisibility,
                second = signature.constructorVisibility,
            ),
            factoryClassName = factoryClassName,
            factoryArgsClassName = factoryArgsClassName.takeIf { validatedParameters.any { it.kind == ParameterKind.FactoryArg } },
            constructorParameters = validatedParameters,
        )
    }

    private fun validateParameter(
        parameter: RawReducerParameter,
        actionTypeName: TypeName,
    ): ValidatedReducerParameter {
        ensureValid(
            condition = !parameter.hasDefault,
            message = "Reducer constructor parameters with default values are not supported",
        )
        ensureValid(
            condition = !parameter.isVararg,
            message = "Reducer constructor vararg parameters are not supported",
        )

        val isCoroutineScope = parameter.name == "coroutineScope"
        val isEmitUserAction = parameter.name == "emitUserAction"
        ensureValid(
            condition = !(parameter.hasRuntimeAnnotation && (isCoroutineScope || isEmitUserAction)),
            message = "@FactoryArg cannot be applied to coroutineScope or emitUserAction",
        )
        ensureValid(
            condition = !(parameter.hasRuntimeAnnotation && parameter.name == "context"),
            message = "@FactoryArg parameter name 'context' is reserved by generated factory methods",
        )

        return when {
            isCoroutineScope -> {
                ensureValid(
                    condition = parameter.typeName == coroutineScopeType,
                    message = "coroutineScope parameter must use kotlinx.coroutines.CoroutineScope",
                )
                ValidatedReducerParameter(
                    name = parameter.name,
                    typeName = parameter.typeName,
                    kind = ParameterKind.CoroutineScope,
                    qualifier = parameter.qualifier,
                )
            }

            isEmitUserAction -> {
                ensureValid(
                    condition = parameter.typeName == LambdaTypeName.get(
                        parameters = arrayOf(actionTypeName),
                        returnType = kotlinUnitType,
                    ),
                    message = "emitUserAction parameter must use the reducer Action type",
                )
                ValidatedReducerParameter(
                    name = parameter.name,
                    typeName = parameter.typeName,
                    kind = ParameterKind.EmitUserAction,
                    qualifier = parameter.qualifier,
                )
            }

            parameter.hasRuntimeAnnotation -> ValidatedReducerParameter(
                name = parameter.name,
                typeName = parameter.typeName,
                kind = ParameterKind.FactoryArg,
                qualifier = parameter.qualifier,
            )

            else -> ValidatedReducerParameter(
                name = parameter.name,
                typeName = parameter.typeName,
                kind = ParameterKind.Injected,
                qualifier = parameter.qualifier,
            )
        }
    }

    private fun ensureValid(
        condition: Boolean,
        message: String,
    ) {
        if (!condition) {
            throw InvalidReducerFactoryException(message)
        }
    }
}

internal fun moreRestrictiveVisibility(
    first: Visibility,
    second: Visibility,
): Visibility {
    return listOf(first, second).maxBy {
        when (it) {
            Visibility.Public -> 0
            Visibility.Internal -> 1
            Visibility.Protected -> 2
            Visibility.Private -> 3
        }
    }
}

internal fun TypeName.parameterizedBy(vararg typeArguments: TypeName): ParameterizedTypeName =
    (this as ClassName).parameterizedBy(*typeArguments)
