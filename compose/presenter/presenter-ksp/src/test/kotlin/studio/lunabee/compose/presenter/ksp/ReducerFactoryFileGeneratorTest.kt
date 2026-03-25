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
import kotlin.test.assertTrue

class ReducerFactoryFileGeneratorTest {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val generator: ReducerFactoryFileGenerator = ReducerFactoryFileGenerator()

    @Test
    fun generate_runtime_factory_file_test() {
        val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val validSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
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

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("data class TimerReducerRuntimeArgs"))
        assertTrue(generatedSource.contains("class TimerReducerFactory"))
        assertTrue(generatedSource.contains("    public val runtimeParam: TimerRuntimeParam,"))
        assertTrue(generatedSource.contains("public fun create("))
        assertTrue(!generatedSource.contains("override fun create("))
        assertTrue(generatedSource.contains("LBSingleReducer<TimerUiState, TimerNavScope, TimerAction>"))
        assertTrue(!generatedSource.contains("): TimerReducer"))
        assertTrue(generatedSource.contains("runtimeParam: TimerRuntimeParam"))
        assertTrue(!generatedSource.contains("\n    ,\n"))
    }

    @Test
    fun generate_runtime_factory_qualifies_injected_properties_test() {
        val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val validSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "CollisionReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
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
                        "runtime",
                        ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedRuntime"),
                        false,
                        false,
                        false,
                    ),
                    RawReducerParameter(
                        "runtimeArgs",
                        ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedRuntimeArgs"),
                        false,
                        false,
                        false,
                    ),
                    RawReducerParameter(
                        "external",
                        ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerExternalRuntimeArg"),
                        true,
                        false,
                        false,
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("runtime = this.runtime"))
        assertTrue(generatedSource.contains("runtimeArgs = this.runtimeArgs"))
        assertTrue(generatedSource.contains("external = runtimeArgs.external"))
    }
}
