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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import studio.lunabee.compose.presenter.ksp.DiQualifier
import studio.lunabee.compose.presenter.ksp.ValidReducerSignature
import studio.lunabee.compose.presenter.ksp.ValidatedReducerParameter

private val koinModuleType: ClassName = ClassName("org.koin.core.module", "Module")
private val koinModuleMember: MemberName = MemberName("org.koin.dsl", "module")
private val koinNamedMember: MemberName = MemberName("org.koin.core.qualifier", "named")
private const val GeneratedKoinModuleFileName = "GeneratedReducerFactoryModule"
private const val GeneratedKoinModulePropertyName = "generatedReducerFactoryModule"

/**
 * Generates the shared Koin module aggregating all generated reducer factories.
 */
internal class KoinModuleFileGenerator {
    /**
     * Builds the `generatedReducerFactoryModule` file registering every factory of [signatures] in
     * [moduleRootPackageName].
     */
    fun generateKoinModule(
        signatures: List<ValidReducerSignature>,
        moduleRootPackageName: String,
    ): FileSpec {
        require(signatures.isNotEmpty()) { "Koin module generation requires at least one reducer signature" }

        val sortedSignatures = signatures.sortedBy { it.factoryClassName.canonicalName }
        return FileSpec.builder(
            packageName = moduleRootPackageName,
            fileName = GeneratedKoinModuleFileName,
        ).indent("    ")
            .addProperty(
                PropertySpec.builder(GeneratedKoinModulePropertyName, koinModuleType)
                    .initializer(buildKoinModuleInitializer(sortedSignatures))
                    .build(),
            )
            .build()
    }

    /**
     * Renders [fileSpec] as Kotlin source code.
     */
    fun render(fileSpec: FileSpec): String = fileSpec.toString()

    private fun buildKoinModuleInitializer(signatures: List<ValidReducerSignature>): CodeBlock =
        CodeBlock.builder()
            .add("%M {\n", koinModuleMember)
            .indent()
            .apply {
                signatures.forEach { signature ->
                    add("factory { %T(", signature.factoryClassName)
                    signature.injectedParameters.forEachIndexed { index, parameter ->
                        if (index > 0) add(", ")
                        add("%L", injectedParameterResolution(parameter))
                    }
                    add(") }\n")
                }
            }.unindent()
            .add("}")
            .build()

    private fun injectedParameterResolution(parameter: ValidatedReducerParameter): CodeBlock = when (val qualifier = parameter.qualifier) {
        null -> CodeBlock.of("get()")

        is DiQualifier.Named -> CodeBlock.of("get(qualifier = %M(%S))", koinNamedMember, qualifier.value)

        is DiQualifier.Typed -> CodeBlock.of(
            "get(qualifier = %M<%T>())",
            koinNamedMember,
            qualifier.annotationClassName,
        )
    }
}

internal fun commonPackageName(packageNames: List<String>): String {
    require(packageNames.isNotEmpty()) { "At least one package name is required" }

    val normalizedPackageNames = packageNames.filter { it.isNotBlank() }
    if (normalizedPackageNames.isEmpty()) return packageNames.first()

    val firstPackageSegments = normalizedPackageNames.first().split('.')
    val commonSegments = firstPackageSegments.indices.takeWhile { index ->
        normalizedPackageNames.drop(1).all { candidate ->
            candidate.split('.').getOrNull(index) == firstPackageSegments[index]
        }
    }.map { firstPackageSegments[it] }

    return commonSegments.joinToString(".").ifEmpty { normalizedPackageNames.first() }
}
