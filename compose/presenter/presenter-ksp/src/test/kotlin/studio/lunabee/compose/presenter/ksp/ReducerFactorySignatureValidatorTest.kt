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
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReducerFactorySignatureValidatorTest {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
    private val uiStateType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState")
    private val navScopeType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope")

    @Test
    fun validate_runtime_parameter_signature_test() {
        val signature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerReducer"),
                uiStateTypeName = uiStateType,
                navScopeTypeName = navScopeType,
                actionTypeName = actionType,
                constructorVisibility = Visibility.Public,
                constructorParameters = listOf(
                    RawReducerParameter(
                        name = "coroutineScope",
                        typeName = ClassName("kotlinx.coroutines", "CoroutineScope"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "emitUserAction",
                        typeName = LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "injectedParam",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedParam"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "runtimeParam",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerRuntimeParam"),
                        hasRuntimeAnnotation = true,
                        hasDefault = false,
                        isVararg = false,
                    ),
                ),
            ),
        )

        assertEquals("TimerReducerFactory", signature.factoryClassName.simpleName)
        assertEquals("TimerReducerFactoryArgs", signature.factoryArgsClassName?.simpleName)
        assertEquals(1, signature.injectedParameters.size)
        assertEquals(1, signature.factoryArgParameters.size)
    }

    @Test
    fun validate_runtime_annotation_on_builtin_parameter_test() {
        val exception = assertFailsWith<InvalidReducerFactoryException> {
            validator.validate(
                RawReducerSignature(
                    packageName = "studio.lunabee.compose.demo.presenter.timer",
                    reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "BrokenReducer"),
                    uiStateTypeName = uiStateType,
                    navScopeTypeName = navScopeType,
                    actionTypeName = actionType,
                    constructorVisibility = Visibility.Public,
                    constructorParameters = listOf(
                        RawReducerParameter(
                            name = "coroutineScope",
                            typeName = ClassName("kotlinx.coroutines", "CoroutineScope"),
                            hasRuntimeAnnotation = true,
                            hasDefault = false,
                            isVararg = false,
                        ),
                        RawReducerParameter(
                            name = "emitUserAction",
                            typeName = LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                            hasRuntimeAnnotation = false,
                            hasDefault = false,
                            isVararg = false,
                        ),
                    ),
                ),
            )
        }

        assertEquals("@FactoryArg cannot be applied to coroutineScope or emitUserAction", exception.message)
    }

    @Test
    fun validate_protected_constructor_signature_test() {
        val exception = assertFailsWith<InvalidReducerFactoryException> {
            validator.validate(
                RawReducerSignature(
                    packageName = "studio.lunabee.compose.demo.presenter.timer",
                    reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "ProtectedReducer"),
                    uiStateTypeName = uiStateType,
                    navScopeTypeName = navScopeType,
                    actionTypeName = actionType,
                    constructorVisibility = Visibility.Protected,
                    constructorParameters = listOf(
                        RawReducerParameter(
                            name = "coroutineScope",
                            typeName = ClassName("kotlinx.coroutines", "CoroutineScope"),
                            hasRuntimeAnnotation = false,
                            hasDefault = false,
                            isVararg = false,
                        ),
                        RawReducerParameter(
                            name = "emitUserAction",
                            typeName = LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                            hasRuntimeAnnotation = false,
                            hasDefault = false,
                            isVararg = false,
                        ),
                    ),
                ),
            )
        }

        assertEquals("Reducer factory generation only supports public or internal constructors", exception.message)
    }

    @Test
    fun validate_factory_arg_annotation_on_reserved_context_parameter_name_test() {
        val exception = assertFailsWith<InvalidReducerFactoryException> {
            validator.validate(
                RawReducerSignature(
                    packageName = "studio.lunabee.compose.demo.presenter.timer",
                    reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "ReservedRuntimeReducer"),
                    uiStateTypeName = uiStateType,
                    navScopeTypeName = navScopeType,
                    actionTypeName = actionType,
                    constructorVisibility = Visibility.Public,
                    constructorParameters = listOf(
                        RawReducerParameter(
                            name = "coroutineScope",
                            typeName = ClassName("kotlinx.coroutines", "CoroutineScope"),
                            hasRuntimeAnnotation = false,
                            hasDefault = false,
                            isVararg = false,
                        ),
                        RawReducerParameter(
                            name = "emitUserAction",
                            typeName = LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                            hasRuntimeAnnotation = false,
                            hasDefault = false,
                            isVararg = false,
                        ),
                        RawReducerParameter(
                            name = "context",
                            typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerRuntimeParam"),
                            hasRuntimeAnnotation = true,
                            hasDefault = false,
                            isVararg = false,
                        ),
                    ),
                ),
            )
        }

        assertEquals("@FactoryArg parameter name 'context' is reserved by generated factory methods", exception.message)
    }
}
