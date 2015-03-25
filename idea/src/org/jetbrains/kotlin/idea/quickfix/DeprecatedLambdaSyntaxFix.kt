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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFully
import org.jetbrains.kotlin.idea.project.PluginJetFilesProvider
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.ShortenReferences
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.psiModificationUtil.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.expressions.FunctionsTypingVisitor
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.ArrayList

public class DeprecatedLambdaSyntaxFix(element: JetFunctionLiteralExpression) : JetIntentionAction<JetFunctionLiteralExpression>(element) {
    override fun getText() = JetBundle.message("migrate.lambda.syntax")
    override fun getFamilyName() = JetBundle.message("migrate.lambda.syntax.family")

    override fun invoke(project: Project, editor: Editor, file: JetFile) {
        DeprecatedSyntaxFix.createFix(element).runFix()
    }

    companion object Factory : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic)
                = (diagnostic.getPsiElement() as? JetFunctionLiteralExpression)?.let { DeprecatedLambdaSyntaxFix(it) }
    }
}

public class DeprecatedLambdaSyntaxInWholeProjectFix(element: JetFunctionLiteralExpression) : JetIntentionAction<JetFunctionLiteralExpression>(element) {
    override fun getText() = JetBundle.message("migrate.lambda.syntax.in.whole.project")
    override fun getFamilyName() = JetBundle.message("migrate.lambda.syntax.in.whole.project.family")

    override fun invoke(project: Project, editor: Editor, file: JetFile) {
        ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Removing deprecated lambda syntax", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val files = runReadAction { PluginJetFilesProvider.allFilesInProject(project) }

                        for ((i, currentFile) in files.withIndex()) {
                            indicator.setText("Checking file $i of ${files.size()}...")
                            indicator.setText2(file.getVirtualFile().getPath())

                            try {
                                val sortedLambdas = runReadAction {
                                    val lambdas = ArrayList<DeprecatedSyntaxFix>()
                                    currentFile.accept(LambdaCollectionVisitor(lambdas), 0)
                                    lambdas.sortBy { -it.level }
                                }
                                UIUtil.invokeAndWaitIfNeeded<Unit> {
                                    project.executeWriteCommand("Removing deprecated lambda syntax") {
                                        val shortenReferences = ArrayList<JetElement>()
                                        sortedLambdas.forEach {
                                            shortenReferences.addAll(it.runWithoutShortenReferences())
                                        }
                                        ShortenReferences.DEFAULT.process(shortenReferences)
                                    }
                                }
                            }
                            catch (e: ProcessCanceledException) {
                                return
                            }
                            catch (e: Throwable) {
                                LOG.error(e)
                            }

                            indicator.setFraction((i + 1) / files.size().toDouble())
                        }
                    }
                }
        )

    }

    private class LambdaCollectionVisitor(val lambdas: MutableCollection<DeprecatedSyntaxFix>) : JetTreeVisitor<Int>() {

        override fun visitFunctionLiteralExpression(functionLiteralExpression: JetFunctionLiteralExpression, data: Int): Void? {
            functionLiteralExpression.acceptChildren(this, data + 1)
            if (JetPsiUtil.isDeprecatedLambdaSyntax(functionLiteralExpression)) {
                lambdas.add(DeprecatedSyntaxFix.createFix(functionLiteralExpression, data))
            }
            return null
        }

        override fun visitJetFile(file: JetFile, data: Int?): Void? {
            super.visitJetFile(file, data)
            file.acceptChildren(this, data)
            return null
        }
    }

    companion object Factory : JetSingleIntentionActionFactory() {
        val LOG = Logger.getInstance(javaClass<DeprecatedLambdaSyntaxInWholeProjectFix>());

        override fun createAction(diagnostic: Diagnostic)
                = (diagnostic.getPsiElement() as? JetFunctionLiteralExpression)?.let { DeprecatedLambdaSyntaxInWholeProjectFix(it) }
    }

    override fun startInWriteAction() = false
}

private trait DeprecatedSyntaxFix {
    val level: Int

    // you must run it under write action
    fun runFix() {
        ShortenReferences.DEFAULT.process(runWithoutShortenReferences())
    }

    fun runWithoutShortenReferences(): Collection<JetElement>

    internal companion object {
        fun createFix(functionLiteralExpression: JetFunctionLiteralExpression, level: Int = 0): DeprecatedSyntaxFix {
            val functionLiteral = functionLiteralExpression.getFunctionLiteral()
            val hasNoReturnAndReceiverType = !functionLiteral.hasDeclaredReturnType() && functionLiteral.getReceiverTypeReference() == null

            return if (hasNoReturnAndReceiverType) DeparenthesizeParameterList(functionLiteralExpression, level)
            else LambdaToFunctionExpression(functionLiteralExpression, level)
        }
    }
}

private class DeparenthesizeParameterList(
        val functionLiteralExpression: JetFunctionLiteralExpression,
        override val level: Int = 0
): DeprecatedSyntaxFix {

    override fun runWithoutShortenReferences(): Collection<JetElement> {
        val psiFactory = JetPsiFactory(functionLiteralExpression)
        val functionLiteral = functionLiteralExpression.getFunctionLiteral()
        val parameterList = functionLiteral.getValueParameterList()
        if (parameterList != null && parameterList.isParenthesized()) {
            val oldParameterList = parameterList.getText()
            val newParameterList = oldParameterList.substring(1..oldParameterList.length() - 2)
            parameterList.replace(psiFactory.createFunctionLiteralParameterList(newParameterList))
        }

        return listOf()
    }
}

private class LambdaToFunctionExpression(
        val functionLiteralExpression: JetFunctionLiteralExpression,
        override val level: Int = 0
): DeprecatedSyntaxFix {
    val functionLiteral = functionLiteralExpression.getFunctionLiteral()
    val psiFactory = JetPsiFactory(functionLiteralExpression)

    val bindingContext = functionLiteralExpression.analyze()
    val functionLiteralType = run {
        val type = bindingContext.get(BindingContext.EXPRESSION_TYPE, functionLiteralExpression)
        assert(type != null && KotlinBuiltIns.isFunctionOrExtensionFunctionType(type)) {
            "Broken function type for expression: ${functionLiteralExpression.getText()}, at: ${DiagnosticUtils.atLocation(functionLiteralExpression)}"
        }
        type
    }

    override fun runWithoutShortenReferences(): Collection<JetElement> {
        if (!JetPsiUtil.isDeprecatedLambdaSyntax(functionLiteralExpression)) return listOf()

        val newFunctionExpression = convertToFunctionExpression(functionLiteralType)

        val literalArgument = getFunctionLiteralArgument()
        val replacedFunctionExpression = if (literalArgument == null) {
            functionLiteralExpression.replace(newFunctionExpression)
        }
        else {
            literalArgument.moveInsideParenthesesAndReplaceWith(newFunctionExpression, bindingContext).
                    getValueArguments().last().getArgumentExpression()
        }

        val functionExpression = JetPsiUtil.deparenthesize(replacedFunctionExpression as JetExpression) as JetNamedFunction

        return listOf(functionExpression.getReceiverTypeReference(), functionExpression.getTypeReference()).filterNotNull()
    }

    private fun getFunctionLiteralArgument(): JetFunctionLiteralArgument? {
        val argument = functionLiteralExpression.getParentOfType<JetFunctionLiteralArgument>(strict = false)

        if (argument != null && argument.getFunctionLiteral() == functionLiteralExpression) {
            return argument
        }
        return null
    }

    private fun JetElement.replaceWithReturn(psiFactory: JetPsiFactory) {
        if (this is JetReturnExpression) {
            return
        }
        else {
            replace(psiFactory.createReturn(getText()))
        }
    }

    private fun getLambdaLabelName(): String? {
        val labeledExpression = functionLiteralExpression.getParentOfType<JetLabeledExpression>(strict = false)
        if (labeledExpression != null && JetPsiUtil.deparenthesize(labeledExpression.getBaseExpression()) == functionLiteralExpression) {
            return labeledExpression.getLabelName()
        }
        return null
    }

    private fun convertToFunctionExpression(
            functionLiteralType: JetType
    ): JetNamedFunction {
        val functionName = getLambdaLabelName()
        val parameterList = functionLiteral.getValueParameterList()?.getText()
        val receiverType = KotlinBuiltIns.getReceiverType(functionLiteralType)?.let { IdeDescriptorRenderers.SOURCE_CODE.renderType(it) }
        val returnType = KotlinBuiltIns.getReturnTypeFromFunctionType(functionLiteralType).let {
            if (KotlinBuiltIns.isUnit(it))
                null
            else
                IdeDescriptorRenderers.SOURCE_CODE.renderType(it)
        }

        val functionDeclaration = "fun " +
                                  (receiverType?.let { "$it." } ?: "") +
                                  (functionName ?: "") +
                                  (parameterList ?: "()") +
                                  (returnType?.let { ": $it" } ?: "")

        val functionWithEmptyBody = psiFactory.createFunction(functionDeclaration + " {}")

        val blockExpression = functionLiteral.getBodyExpression()
        if (blockExpression == null) return functionWithEmptyBody

        val statements = blockExpression.getStatements()
        if (statements.isEmpty()) return functionWithEmptyBody

        if (statements.size() == 1) {
            return psiFactory.createFunction(functionDeclaration + " = " + statements.first().getText())
        }

        // many statements
        if (returnType != null) statements.last().replaceWithReturn(psiFactory)

        return psiFactory.createFunction(functionDeclaration + "{ " + blockExpression.getText() + "}")
    }

}
