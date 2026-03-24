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
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

private val coroutineScopeType: ClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
private val kotlinUnitType: ClassName = ClassName("kotlin", "Unit")

internal data class RawReducerSignature(
    val packageName: String,
    val reducerClassName: ClassName,
    val uiStateTypeName: TypeName,
    val navScopeTypeName: TypeName,
    val actionTypeName: TypeName,
    val constructorVisibility: Visibility,
    val constructorParameters: List<RawReducerParameter>,
)

internal data class RawReducerParameter(
    val name: String,
    val typeName: TypeName,
    val hasRuntimeAnnotation: Boolean,
    val hasDefault: Boolean,
    val isVararg: Boolean,
)

internal data class ValidReducerSignature(
    val packageName: String,
    val reducerClassName: ClassName,
    val uiStateTypeName: TypeName,
    val navScopeTypeName: TypeName,
    val actionTypeName: TypeName,
    val factoryClassName: ClassName,
    val runtimeArgsClassName: ClassName?,
    val constructorParameters: List<ValidatedReducerParameter>,
) {
    val injectedParameters: List<ValidatedReducerParameter> = constructorParameters.filter { it.kind == ParameterKind.Injected }
    val runtimeParameters: List<ValidatedReducerParameter> = constructorParameters.filter { it.kind == ParameterKind.Runtime }
}

internal data class ValidatedReducerParameter(
    val name: String,
    val typeName: TypeName,
    val kind: ParameterKind,
)

internal enum class ParameterKind {
    CoroutineScope,
    EmitUserAction,
    Injected,
    Runtime,
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
        if (signature.constructorVisibility == Visibility.Private) {
            throw InvalidReducerFactoryException("Reducer factory generation does not support private constructors")
        }

        val factoryClassName = ClassName(signature.packageName, "${signature.reducerClassName.simpleName}Factory")
        val runtimeArgsClassName = ClassName(signature.packageName, "${signature.reducerClassName.simpleName}RuntimeArgs")
        val validatedParameters = signature.constructorParameters.map { parameter ->
            validateParameter(
                parameter = parameter,
                actionTypeName = signature.actionTypeName,
            )
        }

        val coroutineScopeCount = validatedParameters.count { it.kind == ParameterKind.CoroutineScope }
        if (coroutineScopeCount != 1) {
            throw InvalidReducerFactoryException("Reducer constructor must declare exactly one coroutineScope: CoroutineScope parameter")
        }

        val emitUserActionCount = validatedParameters.count { it.kind == ParameterKind.EmitUserAction }
        if (emitUserActionCount != 1) {
            throw InvalidReducerFactoryException(
                "Reducer constructor must declare exactly one emitUserAction: (Action) -> Unit parameter",
            )
        }

        return ValidReducerSignature(
            packageName = signature.packageName,
            reducerClassName = signature.reducerClassName,
            uiStateTypeName = signature.uiStateTypeName,
            navScopeTypeName = signature.navScopeTypeName,
            actionTypeName = signature.actionTypeName,
            factoryClassName = factoryClassName,
            runtimeArgsClassName = runtimeArgsClassName.takeIf { validatedParameters.any { it.kind == ParameterKind.Runtime } },
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
            message = "@Runtime cannot be applied to coroutineScope or emitUserAction",
        )

        return when {
            isCoroutineScope -> {
                ensureValid(
                    condition = parameter.typeName == coroutineScopeType,
                    message = "coroutineScope parameter must use kotlinx.coroutines.CoroutineScope",
                )
                ValidatedReducerParameter(parameter.name, parameter.typeName, ParameterKind.CoroutineScope)
            }

            isEmitUserAction -> {
                ensureValid(
                    condition = parameter.typeName == LambdaTypeName.get(
                        parameters = arrayOf(actionTypeName),
                        returnType = kotlinUnitType,
                    ),
                    message = "emitUserAction parameter must use the reducer Action type",
                )
                ValidatedReducerParameter(parameter.name, parameter.typeName, ParameterKind.EmitUserAction)
            }

            parameter.hasRuntimeAnnotation -> ValidatedReducerParameter(parameter.name, parameter.typeName, ParameterKind.Runtime)

            else -> ValidatedReducerParameter(parameter.name, parameter.typeName, ParameterKind.Injected)
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

internal fun TypeName.parameterizedBy(vararg typeArguments: TypeName): ParameterizedTypeName =
    (this as ClassName).parameterizedBy(*typeArguments)
