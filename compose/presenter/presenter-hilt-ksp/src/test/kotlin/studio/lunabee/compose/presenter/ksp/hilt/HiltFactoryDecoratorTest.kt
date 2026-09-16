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

package studio.lunabee.compose.presenter.ksp.hilt

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

private const val Package = "studio.lunabee.compose.demo.presenter.timer"
private val jakartaNamed: ClassName = ClassName("jakarta.inject", "Named")

class HiltFactoryDecoratorTest {
    private val validator: ReducerFactorySignatureValidator = ReducerFactorySignatureValidator()
    private val generator: ReducerFactoryFileGenerator = ReducerFactoryFileGenerator(decorator = HiltFactoryDecorator)
    private val actionType: ClassName = ClassName(Package, "TimerAction")

    @Test
    fun generate_inject_annotated_factory_file_test() {
        val qualifierType = ClassName(Package, "TimerQualifier")
        val validSignature = validator.validate(
            rawSignature(
                parameters = presenterContextParameters() + listOf(
                    RawReducerParameter(
                        name = "plainDependency",
                        typeName = ClassName(Package, "TimerPlainDependency"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                    ),
                    RawReducerParameter(
                        name = "apiClient",
                        typeName = ClassName(Package, "TimerApiClient"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = DiQualifier.Named(value = "api", annotationClassName = ClassName("javax.inject", "Named")),
                    ),
                    RawReducerParameter(
                        name = "qualifierScopedDependency",
                        typeName = ClassName(Package, "TimerQualifierScopedDependency"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = DiQualifier.Typed(qualifierType),
                    ),
                    RawReducerParameter(
                        name = "external",
                        typeName = ClassName(Package, "TimerExternalRuntimeArg"),
                        hasRuntimeAnnotation = true,
                        hasDefault = false,
                        isVararg = false,
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("javax.inject.Inject"))
        assertTrue(generatedSource.contains("@Inject"))
        assertTrue(generatedSource.contains("@Named(\"api\")"))
        assertTrue(generatedSource.contains("@TimerQualifier"))
        // @FactoryArg params stay out of the injectable constructor (they live in the FactoryArgs data class).
        assertFalse(generatedSource.contains("private val `external`"))
        assertTrue(generatedSource.contains("public data class TimerReducerFactoryArgs"))
    }

    @Test
    fun generate_factory_keeping_the_jakarta_named_flavour_test() {
        val validSignature = validator.validate(
            rawSignature(
                parameters = presenterContextParameters() + listOf(
                    RawReducerParameter(
                        name = "apiClient",
                        typeName = ClassName(Package, "TimerApiClient"),
                        hasRuntimeAnnotation = false,
                        hasDefault = false,
                        isVararg = false,
                        qualifier = DiQualifier.Named(value = "api", annotationClassName = jakartaNamed),
                    ),
                ),
            ),
        )

        val generatedSource = generator.render(generator.generate(validSignature))

        assertTrue(generatedSource.contains("jakarta.inject.Named"))
        assertFalse(generatedSource.contains("javax.inject.Named"))
    }

    @Test
    fun generate_inject_constructor_without_injected_dependency_test() {
        val validSignature = validator.validate(rawSignature(parameters = presenterContextParameters()))

        val generatedSource = generator.render(generator.generate(validSignature))

        // Dagger only binds a type through constructor injection when the constructor itself carries @Inject, so the
        // empty constructor has to be emitted even though the factory captures nothing.
        assertTrue(generatedSource.contains("@Inject"))
        assertTrue(generatedSource.contains("constructor()"))
    }

    private fun rawSignature(parameters: List<RawReducerParameter>): RawReducerSignature = RawReducerSignature(
        packageName = Package,
        reducerClassName = ClassName(Package, "TimerReducer"),
        uiStateTypeName = ClassName(Package, "TimerUiState"),
        navScopeTypeName = ClassName(Package, "TimerNavScope"),
        actionTypeName = actionType,
        constructorVisibility = Visibility.Public,
        constructorParameters = parameters,
    )

    private fun presenterContextParameters(): List<RawReducerParameter> = listOf(
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
    )
}
