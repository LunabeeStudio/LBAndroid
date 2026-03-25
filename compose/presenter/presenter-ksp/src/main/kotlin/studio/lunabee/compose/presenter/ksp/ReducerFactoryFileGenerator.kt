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
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private val presenterContextType: ClassName = ClassName("studio.lunabee.compose.presenter", "LBPresenterContext")
private val reducerFactoryType: ClassName = ClassName("studio.lunabee.compose.presenter", "LBSingleReducerFactory")
private val singleReducerType: ClassName = ClassName("studio.lunabee.compose.presenter", "LBSingleReducer")
private const val ContextParam = "context"
private const val FactoryArgsParam = "factoryArgs"
private const val ContextParamKdoc = "@param $ContextParam Values owned by the presenter\n"

internal class ReducerFactoryFileGenerator {
    fun generate(signature: ValidReducerSignature): FileSpec {
        val fileSpec = FileSpec.builder(signature.packageName, signature.factoryClassName.simpleName)
            .indent("    ")
        signature.factoryArgsClassName?.let { factoryArgsClassName ->
            fileSpec.addType(generateFactoryArgsType(signature, factoryArgsClassName))
        }
        fileSpec.addType(generateFactoryType(signature))
        return fileSpec.build()
    }

    fun render(fileSpec: FileSpec): String = fileSpec.toString()

    private fun generateFactoryArgsType(
        signature: ValidReducerSignature,
        factoryArgsClassName: ClassName,
    ): TypeSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(factoryArgsClassName)
            .addModifiers(KModifier.DATA)

        signature.factoryArgParameters.forEach { parameter ->
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

        if (signature.factoryArgsClassName == null) {
            typeBuilder.addSuperinterface(
                reducerFactoryType.parameterizedBy(
                    signature.uiStateTypeName,
                    signature.navScopeTypeName,
                    signature.actionTypeName,
                ),
            )
        }

        typeBuilder.addFunction(generateCreateOverride(signature))
        if (signature.factoryArgParameters.isNotEmpty()) {
            typeBuilder.addFunction(generateConvenienceCreate(signature))
        }

        return typeBuilder.build()
    }

    private fun generateCreateOverride(signature: ValidReducerSignature): FunSpec {
        val functionBuilder = FunSpec.builder("create")
            .returns(publicReducerReturnType(signature))
            .addKdoc(ContextParamKdoc)
            .addParameter(
                ParameterSpec.builder(
                    ContextParam,
                    presenterContextType.parameterizedBy(signature.actionTypeName),
                ).build(),
            )

        if (signature.factoryArgsClassName == null) {
            functionBuilder.addModifiers(KModifier.OVERRIDE)
        }

        signature.factoryArgsClassName?.let { factoryArgsClassName ->
            functionBuilder.addParameter(FactoryArgsParam, factoryArgsClassName)
        }

        functionBuilder.addCode(buildReducerCall(signature))
        return functionBuilder.build()
    }

    private fun generateConvenienceCreate(signature: ValidReducerSignature): FunSpec {
        val functionBuilder = FunSpec.builder("create")
            .returns(publicReducerReturnType(signature))
            .addKdoc(ContextParamKdoc)
            .addParameter(
                ParameterSpec.builder(
                    ContextParam,
                    presenterContextType.parameterizedBy(signature.actionTypeName),
                ).build(),
            )

        signature.factoryArgParameters.forEach { parameter ->
            functionBuilder.addKdoc("@param %L\n", parameter.name)
            functionBuilder.addParameter(parameter.name, parameter.typeName)
        }

        functionBuilder.addCode(
            CodeBlock.builder()
                .add("return create(\n")
                .indent()
                .add("$ContextParam = $ContextParam,\n")
                .add(
                    "$FactoryArgsParam = %T(\n",
                    requireNotNull(signature.factoryArgsClassName),
                )
                .indent()
                .apply {
                    signature.factoryArgParameters.forEach { parameter ->
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

    private fun publicReducerReturnType(signature: ValidReducerSignature) =
        singleReducerType.parameterizedBy(
            signature.uiStateTypeName,
            signature.navScopeTypeName,
            signature.actionTypeName,
        )

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
        ParameterKind.CoroutineScope -> CodeBlock.of("$ContextParam.coroutineScope")
        ParameterKind.EmitUserAction -> CodeBlock.of("$ContextParam.emitUserAction")
        ParameterKind.Injected -> CodeBlock.of("this.%L", parameter.name)
        ParameterKind.FactoryArg -> CodeBlock.of("$FactoryArgsParam.%L", parameter.name)
    }
}
