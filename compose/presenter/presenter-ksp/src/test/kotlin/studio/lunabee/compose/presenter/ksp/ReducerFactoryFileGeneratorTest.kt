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
import kotlin.test.assertFalse
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

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("data class TimerReducerFactoryArgs"))
        assertTrue(generatedSource.contains("class TimerReducerFactory"))
        assertTrue(generatedSource.contains("    public val runtimeParam: TimerRuntimeParam,"))
        assertTrue(generatedSource.contains("public fun create("))
        assertFalse(generatedSource.contains("override fun create("))
        assertTrue(generatedSource.contains("factoryArgs: TimerReducerFactoryArgs"))
        assertEquals(
            2,
            generatedSource.split("TimerReducer =").size - 1,
        )
        assertFalse(
            generatedSource.contains("): LBSingleReducer<TimerUiState, TimerNavScope, TimerAction>"),
        )
        assertTrue(generatedSource.contains("runtimeParam: TimerRuntimeParam"))
        assertFalse(generatedSource.contains("\n    ,\n"))
    }

    @Test
    fun generate_internal_factory_file_test() {
        val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val validSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "InternalTimerReducer"),
                uiStateTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerUiState"),
                navScopeTypeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerNavScope"),
                actionTypeName = actionType,
                reducerVisibility = Visibility.Internal,
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
                        name = "runtimeParam",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerRuntimeParam"),
                        hasRuntimeAnnotation = true,
                        hasDefault = false,
                        isVararg = false,
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("internal data class InternalTimerReducerFactoryArgs"))
        assertTrue(generatedSource.contains("internal class InternalTimerReducerFactory"))
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
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedRuntime"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "factoryArgs",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedRuntimeArgs"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "external",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerExternalRuntimeArg"),
                        hasRuntimeAnnotation = true,
                        hasDefault = false,
                        isVararg = false,
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("context = this.context"))
        assertTrue(generatedSource.contains("factoryArgs = this.factoryArgs"))
        assertTrue(generatedSource.contains("external = factoryArgs.external"))
    }

    @Test
    fun generate_factory_with_koin_annotations_test() {
        val actionType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerAction")
        val qualifierType = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier")
        val validSignature = validator.validate(
            RawReducerSignature(
                packageName = "studio.lunabee.compose.demo.presenter.timer",
                reducerClassName = ClassName("studio.lunabee.compose.demo.presenter.timer", "AnnotatedTimerReducer"),
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
                        name = "injectedParam",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerInjectedParam"),
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
                        qualifier = KoinQualifier.Named("api"),
                    ),
                    RawReducerParameter(
                        name = "qualifierScopedDependency",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifierScopedDependency"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = KoinQualifier.Typed(qualifierType),
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

        val generatedSource = generator.render(generator.generate(signature = validSignature, useKoinAnnotations = true))

        assertTrue(generatedSource.contains("org.koin.core.`annotation`.Factory"))
        assertTrue(generatedSource.contains("org.koin.core.`annotation`.Named"))
        assertTrue(generatedSource.contains("@Factory\npublic class AnnotatedTimerReducerFactory"))
        assertTrue(generatedSource.contains("@Named(\"api\")\n    private val apiClient: TimerApiClient,"))
        assertTrue(
            generatedSource.contains(
                "@Named(type = TimerQualifier::class)\n" +
                    "    private val qualifierScopedDependency: TimerQualifierScopedDependency,",
            ),
        )
        assertFalse(generatedSource.contains("@Named") && generatedSource.contains("@Named\n    private val injectedParam"))
    }

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
                        qualifier = KoinQualifier.Named("api"),
                    ),
                    RawReducerParameter(
                        name = "qualifierScopedDependency",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifierScopedDependency"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = KoinQualifier.Typed(qualifierType),
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
