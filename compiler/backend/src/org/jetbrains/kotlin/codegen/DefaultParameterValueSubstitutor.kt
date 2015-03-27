/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.codegen.context.CodegenContext
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.JetTypeMapper
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.psi.JetClassOrObject
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

public class DefaultParameterValueSubstitutor(val state: GenerationState) {
    fun generateDefaultConstructorIfNeeded(constructorDescriptor: ConstructorDescriptor,
                                           classBuilder: ClassBuilder,
                                           classOrObject: JetClassOrObject) {
        val method = state.getTypeMapper().mapToCallableMethod(constructorDescriptor)
        if (!isEmptyConstructorNeeded(constructorDescriptor, classOrObject)) {
            return
        }

        generateOverloadWithSubstitutedParameters(method, constructorDescriptor, classBuilder, classOrObject,
                                                  CodegenContext.STATIC,  // this is used to detect whether the call is in the same module, which is always true here
                                                  constructorDescriptor.countDefaultParameters())
    }

    fun generateOverloadsIfNeeded(functionDescriptor: FunctionDescriptor, owner: CodegenContext<*>,
                                  classBuilder: ClassBuilder) {
        val overloadsFqName = FqName.fromSegments(listOf("kotlin", "jvm", "overloads"))
        if (functionDescriptor.getAnnotations().findAnnotation(overloadsFqName) == null) return

        val count = functionDescriptor.countDefaultParameters()
        val context = owner.intoFunction(functionDescriptor)
        val method = state.getTypeMapper().mapToCallableMethod(functionDescriptor, false, context)

        for (i in 1..count) {
            generateOverloadWithSubstitutedParameters(method, functionDescriptor, classBuilder, null, context, i)
        }
    }

    private fun FunctionDescriptor.countDefaultParameters() =
        getValueParameters().count { it.hasDefaultValue() }

    fun generateOverloadWithSubstitutedParameters(method: CallableMethod,
                                                  functionDescriptor: FunctionDescriptor,
                                                  classBuilder: ClassBuilder,
                                                  classOrObject: JetClassOrObject?,
                                                  context: CodegenContext<*>,
                                                  substituteCount: Int) {
        val flags = AsmUtil.getVisibilityAccessFlag(functionDescriptor)
        val remainingParameters = getRemainingParameters(functionDescriptor.getOriginal(), substituteCount)
        val signature = state.getTypeMapper().mapSignature(functionDescriptor, OwnerKind.IMPLEMENTATION,
                                                           remainingParameters)
        val mv = classBuilder.newMethod(OtherOrigin(functionDescriptor), flags,
                                        signature.getAsmMethod().getName(),
                                        signature.getAsmMethod().getDescriptor(), null,
                                        FunctionCodegen.getThrownExceptions(functionDescriptor, state.getTypeMapper()))

        if (state.getClassBuilderMode() == ClassBuilderMode.LIGHT_CLASSES) return

        val v = InstructionAdapter(mv)
        mv.visitCode()

        val methodOwner = method.getOwner()
        v.load(0, methodOwner) // Load this on stack

        var mask = 0
        val masks = arrayListOf<Int>()
        for (parameterDescriptor in functionDescriptor.getValueParameters()) {
            val paramType = state.getTypeMapper().mapType(parameterDescriptor.getType())
            AsmUtil.pushDefaultValueOnStack(paramType, v)
            val i = parameterDescriptor.getIndex()
            if (i != 0 && i % Integer.SIZE == 0) {
                masks.add(mask)
                mask = 0
            }
            mask = mask or (1 shl (i % Integer.SIZE))
        }
        masks.add(mask)
        for (m in masks) {
            v.iconst(m)
        }

        // constructors with default arguments has last synthetic argument of specific type
        if (functionDescriptor is ConstructorDescriptor) {
            v.aconst(null)
        }

        val defaultMethod = state.getTypeMapper().mapDefaultMethod(functionDescriptor, OwnerKind.IMPLEMENTATION, context)
        if (functionDescriptor is ConstructorDescriptor) {
            v.invokespecial(methodOwner.getInternalName(), defaultMethod.getName(), defaultMethod.getDescriptor(), false)
        } else {
            v.invokestatic(methodOwner.getInternalName(), defaultMethod.getName(), defaultMethod.getDescriptor(), false)
        }
        v.areturn(signature.getReturnType())
        FunctionCodegen.endVisit(mv, "default constructor for " + methodOwner.getInternalName(), classOrObject)
    }

    private fun getRemainingParameters(functionDescriptor: FunctionDescriptor,
                                       substituteCount: Int): List<ValueParameterDescriptor> {
        var remainingCount = functionDescriptor.countDefaultParameters() - substituteCount
        return functionDescriptor.getValueParameters().filter { !it.declaresDefaultValue() || --remainingCount >= 0 }
    }

    private fun isEmptyConstructorNeeded(constructorDescriptor: ConstructorDescriptor, classOrObject: JetClassOrObject): Boolean {
        val classDescriptor = constructorDescriptor.getContainingDeclaration()

        if (classOrObject.isLocal()) return false

        if (CodegenBinding.canHaveOuter(state.getBindingContext(), classDescriptor)) return false

        if (Visibilities.isPrivate(classDescriptor.getVisibility()) || Visibilities.isPrivate(constructorDescriptor.getVisibility()))
            return false

        if (constructorDescriptor.getValueParameters().isEmpty()) return false
        if (classOrObject is JetClass && hasSecondaryConstructorsWithNoParameters(classOrObject)) return false

        return constructorDescriptor.getValueParameters().all { it.declaresDefaultValue() }
    }

    private fun hasSecondaryConstructorsWithNoParameters(klass: JetClass) =
        klass.getSecondaryConstructors().any { it.getValueParameters().isEmpty() }

}
