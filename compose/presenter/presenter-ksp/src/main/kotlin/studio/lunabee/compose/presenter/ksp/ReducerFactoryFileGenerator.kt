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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private val reducerRuntimeType: ClassName = ClassName("studio.lunabee.compose.presenter", "LBReducerRuntime")
private val reducerFactoryType: ClassName = ClassName("studio.lunabee.compose.presenter", "LBSingleReducerFactory")

internal class ReducerFactoryFileGenerator {
    fun generate(signature: ValidReducerSignature): FileSpec {
        val fileSpec = FileSpec.builder(signature.packageName, signature.factoryClassName.simpleName)
        signature.runtimeArgsClassName?.let { runtimeArgsClassName ->
            fileSpec.addType(generateRuntimeArgsType(signature, runtimeArgsClassName))
        }
        fileSpec.addType(generateFactoryType(signature))
        return fileSpec.build()
    }

    private fun generateRuntimeArgsType(
        signature: ValidReducerSignature,
        runtimeArgsClassName: ClassName,
    ): TypeSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(runtimeArgsClassName)
            .addModifiers(KModifier.DATA)

        signature.runtimeParameters.forEach { parameter ->
            constructorBuilder.addParameter(parameter.name, parameter.typeName)
            typeBuilder.addProperty(
                PropertySpec.builder(parameter.name, parameter.typeName)
                    .initializer(parameter.name)
                    .build(),
            )
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generateFactoryType(signature: ValidReducerSignature): TypeSpec {
        val typeBuilder = TypeSpec.classBuilder(signature.factoryClassName)
        val constructorBuilder = FunSpec.constructorBuilder()

        signature.injectedParameters.forEach { parameter ->
            constructorBuilder.addParameter(parameter.name, parameter.typeName)
            typeBuilder.addProperty(
                PropertySpec.builder(parameter.name, parameter.typeName)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(parameter.name)
                    .build(),
            )
        }
        if (signature.injectedParameters.isNotEmpty()) {
            typeBuilder.primaryConstructor(constructorBuilder.build())
        }

        if (signature.runtimeArgsClassName == null) {
            typeBuilder.addSuperinterface(
                reducerFactoryType.parameterizedBy(
                    signature.uiStateTypeName,
                    signature.navScopeTypeName,
                    signature.actionTypeName,
                ),
            )
        }

        typeBuilder.addFunction(generateCreateOverride(signature))
        if (signature.runtimeParameters.isNotEmpty()) {
            typeBuilder.addFunction(generateConvenienceCreate(signature))
        }

        return typeBuilder.build()
    }

    private fun generateCreateOverride(signature: ValidReducerSignature): FunSpec {
        val functionBuilder = FunSpec.builder("create")
            .returns(signature.reducerClassName)
            .addParameter(
                ParameterSpec.builder(
                    "runtime",
                    reducerRuntimeType.parameterizedBy(signature.actionTypeName),
                ).build(),
            )

        if (signature.runtimeArgsClassName == null) {
            functionBuilder.addModifiers(KModifier.OVERRIDE)
        }

        signature.runtimeArgsClassName?.let { runtimeArgsClassName ->
            functionBuilder.addParameter("runtimeArgs", runtimeArgsClassName)
        }

        functionBuilder.addCode(buildReducerCall(signature))
        return functionBuilder.build()
    }

    private fun generateConvenienceCreate(signature: ValidReducerSignature): FunSpec {
        val functionBuilder = FunSpec.builder("create")
            .returns(signature.reducerClassName)
            .addKdoc("@param runtime runtime owned by the presenter\n")
            .addParameter(
                ParameterSpec.builder(
                    "runtime",
                    reducerRuntimeType.parameterizedBy(signature.actionTypeName),
                ).build(),
            )

        signature.runtimeParameters.forEach { parameter ->
            functionBuilder.addKdoc("@param %L\n", parameter.name)
            functionBuilder.addParameter(parameter.name, parameter.typeName)
        }

        functionBuilder.addCode(
            CodeBlock.builder()
                .add("return create(\n")
                .indent()
                .add("runtime = runtime,\n")
                .add(
                    "runtimeArgs = %T(\n",
                    requireNotNull(signature.runtimeArgsClassName),
                )
                .indent()
                .apply {
                    signature.runtimeParameters.forEach { parameter ->
                        add("%L = %L,\n", parameter.name, parameter.name)
                    }
                }.unindent()
                .add("),\n")
                .unindent()
                .add(")\n")
                .build(),
        )
        return functionBuilder.build()
    }

    private fun buildReducerCall(signature: ValidReducerSignature): CodeBlock {
        return CodeBlock.builder()
            .add("return %T(\n", signature.reducerClassName)
            .indent()
            .apply {
                signature.constructorParameters.forEach { parameter ->
                    add("%L = %L,\n", parameter.name, constructorValueExpression(parameter))
                }
            }.unindent()
            .add(")\n")
            .build()
    }

    private fun constructorValueExpression(parameter: ValidatedReducerParameter): CodeBlock = when (parameter.kind) {
        ParameterKind.CoroutineScope -> CodeBlock.of("runtime.coroutineScope")
        ParameterKind.EmitUserAction -> CodeBlock.of("runtime.emitUserAction")
        ParameterKind.Injected -> CodeBlock.of("%L", parameter.name)
        ParameterKind.Runtime -> CodeBlock.of("runtimeArgs.%L", parameter.name)
    }
}
