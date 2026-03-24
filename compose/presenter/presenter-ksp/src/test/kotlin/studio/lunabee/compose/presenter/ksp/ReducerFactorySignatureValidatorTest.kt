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
import org.junit.Test
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
                        "coroutineScope",
                        ClassName("kotlinx.coroutines", "CoroutineScope"),
                        false,
                        false,
                        false,
                    ),
                    RawReducerParameter(
                        "emitUserAction",
                        LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                        false,
                        false,
                        false,
                    ),
                    RawReducerParameter(
                        "injectedParam",
                        ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedParam"),
                        false,
                        false,
                        false,
                    ),
                    RawReducerParameter(
                        "runtimeParam",
                        ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerRuntimeParam"),
                        true,
                        false,
                        false,
                    ),
                ),
            ),
        )

        assertEquals("TimerReducerFactory", signature.factoryClassName.simpleName)
        assertEquals("TimerReducerRuntimeArgs", signature.runtimeArgsClassName?.simpleName)
        assertEquals(1, signature.injectedParameters.size)
        assertEquals(1, signature.runtimeParameters.size)
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
                            "coroutineScope",
                            ClassName("kotlinx.coroutines", "CoroutineScope"),
                            true,
                            false,
                            false,
                        ),
                        RawReducerParameter(
                            "emitUserAction",
                            LambdaTypeName.get(parameters = arrayOf(actionType), returnType = Unit::class.asTypeName()),
                            false,
                            false,
                            false,
                        ),
                    ),
                ),
            )
        }

        assertEquals("@Runtime cannot be applied to coroutineScope or emitUserAction", exception.message)
    }
}
