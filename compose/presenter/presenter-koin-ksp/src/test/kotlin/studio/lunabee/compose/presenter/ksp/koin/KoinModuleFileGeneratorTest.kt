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

package studio.lunabee.compose.presenter.ksp.koin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.asTypeName
import studio.lunabee.compose.presenter.ksp.DiQualifier
import studio.lunabee.compose.presenter.ksp.RawReducerParameter
import studio.lunabee.compose.presenter.ksp.RawReducerSignature
import studio.lunabee.compose.presenter.ksp.ReducerFactorySignatureValidator
import studio.lunabee.compose.presenter.ksp.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoinModuleFileGeneratorTest {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val generator: KoinModuleFileGenerator = KoinModuleFileGenerator()

    @Test
    fun generate_koin_module_preserves_qualifiers_test() {
        val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val qualifierType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier")
        val validSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "QualifiedTimerReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
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
                        name = "apiClient",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerApiClient"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = DiQualifier.Named("api"),
                    ),
                    RawReducerParameter(
                        name = "qualifierScopedDependency",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifierScopedDependency"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = DiQualifier.Typed(qualifierType),
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(
            generator.generateKoinModule(
                signatures = listOf(validSignature),
                moduleRootPackageName = "studio.lunabee.compose.demo",
            ),
        )

        assertTrue(generatedSource.contains("get(qualifier = named(\"api\"))"))
        assertTrue(generatedSource.contains("qualifier ="))
        assertTrue(generatedSource.contains("named<TimerQualifier>()"))
    }

    @Test
    fun generate_koin_module_file_test() {
        val timerActionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val timerSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
                actionTypeName = timerActionType,
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
                        typeName = LambdaTypeName.get(parameters = arrayOf(timerActionType), returnType = Unit::class.asTypeName()),
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
        val simpleActionType = ClassName("studio.lunabee.compose.demo.presenter.simple", "SimpleAction")
        val simpleSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.simple",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.simple", "SimpleReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.simple", "SimpleUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.simple", "SimpleNavScope"),
                actionTypeName = simpleActionType,
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
                        typeName = LambdaTypeName.get(parameters = arrayOf(simpleActionType), returnType = Unit::class.asTypeName()),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                ),
            ),
        )

        val fileSpec = generator.generateKoinModule(
            signatures = listOf(timerSignature, simpleSignature),
            moduleRootPackageName = "studio.lunabee.compose.demo",
        )
        val generatedSource = generator.render(fileSpec)

        assertEquals("studio.lunabee.compose.demo", fileSpec.packageName)
        assertTrue(generatedSource.contains("public val generatedReducerFactoryModule: Module = module {"))
        assertTrue(generatedSource.contains("factory { SimpleReducerFactory() }"))
        assertTrue(generatedSource.contains("factory { TimerReducerFactory(get()) }"))
    }

    @Test
    fun common_package_name_test() {
        assertEquals(
            "studio.lunabee.compose.demo",
            commonPackageName(
                listOf(
                    "studio.lunabee.compose.demo.presenter.timer",
                    "studio.lunabee.compose.demo.presenter.simple",
                    "studio.lunabee.compose.demo.navigation",
                ),
            ),
        )
    }
}
