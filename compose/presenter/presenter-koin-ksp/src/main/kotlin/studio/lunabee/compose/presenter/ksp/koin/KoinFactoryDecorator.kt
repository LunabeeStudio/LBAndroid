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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import studio.lunabee.compose.presenter.ksp.DiQualifier
import studio.lunabee.compose.presenter.ksp.GeneratedFactoryDecorator
import studio.lunabee.compose.presenter.ksp.ValidReducerSignature
import studio.lunabee.compose.presenter.ksp.ValidatedReducerParameter

private val koinFactoryAnnotation: ClassName = ClassName("org.koin.core.annotation", "Factory")
private val koinNamedAnnotation: ClassName = ClassName("org.koin.core.annotation", "Named")
private val koinProvidedAnnotation: ClassName = ClassName("org.koin.core.annotation", "Provided")

/**
 * Annotates the generated factory class with [org.koin.core.annotation.Factory] so it can be picked up by a Koin
 * `@ComponentScan` (including across module boundaries from compiled jars), instead of relying on same-module
 * unannotated auto-binding. Koin qualifiers declared on injected reducer constructor parameters are propagated onto the
 * generated factory constructor parameters.
 */
internal object KoinFactoryDecorator : GeneratedFactoryDecorator {
    /**
     * Adds [org.koin.core.annotation.Factory] on the generated factory class.
     */
    override fun classAnnotations(signature: ValidReducerSignature): List<AnnotationSpec> =
        listOf(AnnotationSpec.builder(koinFactoryAnnotation).build())

    /**
     * Propagates the Koin qualifier and the [org.koin.core.annotation.Provided] marker of [parameter] onto the
     * generated factory constructor parameter, so a @Provided reducer dependency stays excluded from Koin
     * compile-time safety on the generated factory too.
     */
    override fun parameterAnnotations(parameter: ValidatedReducerParameter): List<AnnotationSpec> =
        listOfNotNull(
            qualifierAnnotation(parameter.qualifier),
            providedAnnotation(parameter.isProvided),
        )

    private fun providedAnnotation(isProvided: Boolean): AnnotationSpec? =
        AnnotationSpec.builder(koinProvidedAnnotation).build().takeIf { isProvided }

    private fun qualifierAnnotation(qualifier: DiQualifier?): AnnotationSpec? = when (qualifier) {
        null -> null

        is DiQualifier.Named -> AnnotationSpec.builder(koinNamedAnnotation)
            .addMember("%S", qualifier.value)
            .build()

        is DiQualifier.Typed -> AnnotationSpec.builder(qualifier.annotationClassName).build()
    }
}
