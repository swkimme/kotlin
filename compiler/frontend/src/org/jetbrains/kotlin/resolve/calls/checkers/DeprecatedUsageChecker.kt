package org.jetbrains.kotlin.resolve.calls.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.JetElement
import org.jetbrains.kotlin.psi.JetExpression
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.createDeprecationDiagnostic
import org.jetbrains.kotlin.resolve.getDeprecatedAnnotation
import kotlin.platform.platformStatic

public class DeprecatedUsageChecker : CallChecker {
    override fun <F : CallableDescriptor> check(resolvedCall: ResolvedCall<F>, context: BasicCallResolutionContext) {
        check(resolvedCall, context.trace)
    }

    class object {
        platformStatic
        fun <F : CallableDescriptor> check(resolvedCall: ResolvedCall<F>, trace: BindingTrace, element: PsiElement? = null) {
            val targetDescriptor = resolvedCall.getResultingDescriptor()
            val deprecated = targetDescriptor.getDeprecatedAnnotation()
            if (deprecated != null) {
                val call = resolvedCall.getCall()
                val reportElement = element ?: call.getCalleeExpression()
                if (reportElement != null) {
                    trace.report(createDeprecationDiagnostic(reportElement, deprecated))
                }
            }
        }
    }
}