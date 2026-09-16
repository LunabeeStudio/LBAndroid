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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import studio.lunabee.compose.presenter.ksp.DiQualifier
import studio.lunabee.compose.presenter.ksp.GeneratedFactoryDecorator
import studio.lunabee.compose.presenter.ksp.ValidReducerSignature
import studio.lunabee.compose.presenter.ksp.ValidatedReducerParameter

private val injectAnnotation: ClassName = ClassName("javax.inject", "Inject")
private val namedAnnotation: ClassName = ClassName("javax.inject", "Named")

/**
 * Annotates the generated factory primary constructor with `@javax.inject.Inject` so Dagger/Hilt binds the factory
 * through constructor injection, without any hand-written module. Qualifiers declared on injected reducer constructor
 * parameters are propagated onto the generated factory constructor parameters so the factory resolves the same
 * bindings as the reducer would.
 *
 * The constructor annotation is emitted even for a reducer with no injected dependency: Dagger only considers a type
 * for constructor injection when the constructor itself carries `@Inject`.
 */
internal object HiltFactoryDecorator : GeneratedFactoryDecorator {
    override fun classAnnotations(signature: ValidReducerSignature): List<AnnotationSpec> = emptyList()

    /**
     * Adds `@javax.inject.Inject` on the generated factory primary constructor.
     */
    override fun constructorAnnotations(signature: ValidReducerSignature): List<AnnotationSpec> =
        listOf(AnnotationSpec.builder(injectAnnotation).build())

    /**
     * Propagates the qualifier declared on [parameter] onto the generated factory constructor parameter.
     */
    override fun parameterAnnotations(parameter: ValidatedReducerParameter): List<AnnotationSpec> =
        listOfNotNull(qualifierAnnotation(parameter.qualifier))

    private fun qualifierAnnotation(qualifier: DiQualifier?): AnnotationSpec? = when (qualifier) {
        null -> null

        is DiQualifier.Named -> AnnotationSpec.builder(namedAnnotation)
            .addMember("%S", qualifier.value)
            .build()

        is DiQualifier.Typed -> AnnotationSpec.builder(qualifier.annotationClassName).build()
    }
}
