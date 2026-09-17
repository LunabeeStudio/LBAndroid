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

package studio.lunabee.synchronization.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Reports a sync request made from inside an engine callback, where the operator's non-reentrant lock is
 * already held by the run executing that callback. The library refuses such a request at runtime
 * (`LBSyncReentrantCallException`); this rule moves the diagnosis to compile time.
 *
 * A callback is recognized by the `@SyncEngineCallback` annotation the library puts on every SPI member
 * its pipeline calls, so the rule follows the annotated set instead of a hardcoded name list. A request
 * launched through a coroutine builder is skipped: that is the documented fire-and-forget escape, and
 * telling the detached scopes apart from the run's own scope needs more than syntax.
 */
class SyncOperatorReentrantCallDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("sync", "syncAllManagers", "syncGroup")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != OperatorClass) return
        val callback = node.getParentOfType(UMethod::class.java) ?: return
        if (!callback.isEngineCallback()) return
        if (node.isLaunchedDetached(callback)) return

        context.report(
            issue = issue,
            scope = node,
            location = context.getLocation(node),
            message = "`LBSyncOperator.${method.name}()` is called from `${callback.name}()`, which the sync " +
                "engine runs while holding its lock. The request is refused at runtime " +
                "(`LBSyncReentrantCallException`): register the dependency as an earlier `LBSyncGroup`, or " +
                "launch the request on a scope of your own.",
        )
    }

    private fun UMethod.isEngineCallback(): Boolean =
        javaPsi.isAnnotatedAsCallback() || javaPsi.findSuperMethods().any { it.isAnnotatedAsCallback() }

    private fun PsiMethod.isAnnotatedAsCallback(): Boolean =
        annotations.any { it.qualifiedName == CallbackAnnotation }

    private fun UCallExpression.isLaunchedDetached(callback: UMethod): Boolean {
        var element: UElement? = uastParent
        while (element != null && element != callback) {
            if (element is ULambdaExpression && (element.uastParent as? UCallExpression)?.methodName in CoroutineBuilders) {
                return true
            }
            element = element.uastParent
        }
        return false
    }

    companion object {
        private const val OperatorClass: String = "studio.lunabee.synchronization.LBSyncOperator"
        private const val CallbackAnnotation: String = "studio.lunabee.synchronization.SyncEngineCallback"
        private val CoroutineBuilders: Set<String> = setOf("launch", "async")

        val issue: Issue = Issue.create(
            id = "SyncOperatorReentrantCall",
            briefDescription = "Sync request made from inside a sync engine callback",
            explanation = """
                `LBSyncOperator` serializes every sync request behind a lock it holds for the whole run, \
                and that lock is not reentrant. A request made from inside an engine callback \
                (`fetchRequest`, `pushObjectsToServer`, … — anything annotated `@SyncEngineCallback`) is \
                therefore refused with an `LBSyncReentrantCallException`, since honouring it would \
                deadlock the operator for the lifetime of the process.

                Model the dependency as an earlier `LBSyncGroup` — groups run sequentially, in \
                registration order — or launch the request on a scope of your own so it queues behind the \
                run instead of inside it.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                SyncOperatorReentrantCallDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
