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
import studio.lunabee.compose.presenter.ksp.ReducerFactoryFileGenerator
import studio.lunabee.compose.presenter.ksp.ReducerFactorySignatureValidator
import studio.lunabee.compose.presenter.ksp.Visibility
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KoinFactoryDecoratorTest {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()

    @Test
    fun generate_annotated_factory_file_test() {
        val annotatingGenerator = ReducerFactoryFileGenerator(decorator = KoinFactoryDecorator)
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
                        name = "plainDependency",
                        typeName = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerPlainDependency"),
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

        val generatedSource = annotatingGenerator.render(annotatingGenerator.generate(validSignature))

        assertTrue(generatedSource.contains("org.koin.core.`annotation`.Factory"))
        assertTrue(generatedSource.contains("@Factory\npublic class AnnotatedTimerReducerFactory"))
        assertTrue(generatedSource.contains("@Named(\"api\")"))
        assertTrue(generatedSource.contains("@TimerQualifier"))
        // @FactoryArg params stay out of the injectable constructor (they live in the FactoryArgs data class).
        assertFalse(generatedSource.contains("private val `external`"))
    }
}
