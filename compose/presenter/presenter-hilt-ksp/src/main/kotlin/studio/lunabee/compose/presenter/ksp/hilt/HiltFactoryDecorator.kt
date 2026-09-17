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
import studio.lunabee.compose.presenter.ksp.javaxNamedAnnotation

private val injectAnnotation: ClassName = ClassName("javax.inject", "Inject")
private val jakartaNamedAnnotation: ClassName = ClassName("jakarta.inject", "Named")

internal object HiltFactoryDecorator : GeneratedFactoryDecorator {
    override fun classAnnotations(signature: ValidReducerSignature): List<AnnotationSpec> = emptyList()

    override fun constructorAnnotations(signature: ValidReducerSignature): List<AnnotationSpec> =
        listOf(AnnotationSpec.builder(injectAnnotation).build())

    override fun parameterAnnotations(parameter: ValidatedReducerParameter): List<AnnotationSpec> =
        listOfNotNull(qualifierAnnotation(parameter.qualifier))

    private fun qualifierAnnotation(qualifier: DiQualifier?): AnnotationSpec? = when (qualifier) {
        null -> null

        is DiQualifier.Named -> AnnotationSpec.builder(namedAnnotation(qualifier.annotationClassName))
            .addMember("%S", qualifier.value)
            .build()

        is DiQualifier.Typed -> AnnotationSpec.builder(qualifier.annotationClassName).build()
    }

    private fun namedAnnotation(declaredAnnotation: ClassName): ClassName =
        declaredAnnotation.takeIf { it == jakartaNamedAnnotation } ?: javaxNamedAnnotation
}
