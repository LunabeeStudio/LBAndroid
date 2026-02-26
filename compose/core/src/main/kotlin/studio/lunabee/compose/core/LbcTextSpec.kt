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
package studio.lunabee.compose.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.annotation.Discouraged
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString

@Stable
sealed class LbcTextSpec {

    abstract val annotated: AnnotatedString
        @ReadOnlyComposable @Composable
        get

    abstract val string: String
        @ReadOnlyComposable @Composable
        get

    @Deprecated(
        """
        Use string(resources: Resources) instead with LocalResources.current
        See https://googlesamples.github.io/android-custom-lint-rules/checks/LocalContextResourcesRead.md.html
    """,
    )
    fun string(context: Context): String = string(context.resources)

    abstract fun string(resources: Resources): String

    @Deprecated(
        """
        Use annotated(resources: Resources) instead with LocalResources.current
        See https://googlesamples.github.io/android-custom-lint-rules/checks/LocalContextResourcesRead.md.html
    """,
    )
    fun annotated(context: Context): AnnotatedString = annotated(context.resources)

    open fun annotated(resources: Resources): AnnotatedString = AnnotatedString(string(resources))

    @Composable
    @ReadOnlyComposable
    protected fun Array<out Any>.resolveArgs(): Array<out Any> = Array(this.size) { idx ->
        val entry = this[idx]
        if (entry is LbcTextSpec) {
            entry.string
        } else {
            this[idx]
        }
    }

    protected fun Array<out Any>.resolveArgsContext(resources: Resources): Array<out Any> = Array(this.size) { idx ->
        val entry = this[idx]
        if (entry is LbcTextSpec) {
            entry.string(resources)
        } else {
            this[idx]
        }
    }

    class Raw(private val value: String, private vararg val args: Any) : LbcTextSpec() {
        override val annotated: AnnotatedString
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = AnnotatedString(string)

        override val string: String
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = if (args.isEmpty()) {
                value
            } else {
                value.format(*args.resolveArgs())
            }

        override fun string(resources: Resources): String = if (args.isEmpty()) {
            value
        } else {
            @Suppress("SpreadOperator")
            value.format(*args.resolveArgsContext(resources))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Raw

            if (value != other.value) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }

        override fun toString(): String = "value = $value"
    }

    class Annotated(private val value: AnnotatedString) : LbcTextSpec() {
        override val annotated: AnnotatedString
            @Composable
            @ReadOnlyComposable
            get() = value

        override val string: String
            @Composable
            @ReadOnlyComposable
            get() = value.text

        override fun string(resources: Resources): String = value.text

        override fun annotated(resources: Resources): AnnotatedString = this.value

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Annotated

            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "value = ${value.text}"
    }

    class StringResource(
        @StringRes val id: Int,
        vararg val args: Any,
    ) : LbcTextSpec() {
        override val annotated: AnnotatedString
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = AnnotatedString(string)

        override val string: String
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = stringResource(id, *args.resolveArgs())

        override fun string(resources: Resources): String {
            @Suppress("SpreadOperator")
            return resources.getString(id, *args.resolveArgsContext(resources))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StringResource

            if (id != other.id) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    class PluralsResource(
        @PluralsRes val id: Int,
        val count: Int,
        vararg val args: Any,
    ) : LbcTextSpec() {

        override val annotated: AnnotatedString
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = AnnotatedString(string)

        override val string: String
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = pluralStringResource(id, count, *args.resolveArgs())

        override fun string(resources: Resources): String {
            @Suppress("SpreadOperator")
            return resources.getQuantityString(id, count, *args.resolveArgsContext(resources))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PluralsResource

            if (id != other.id) return false
            if (count != other.count) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + count
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    @SuppressLint("DiscouragedApi")
    @Discouraged(
        message = (
            "Use of this function is discouraged because resource reflection makes "
                + "it harder to perform build optimizations and compile-time "
                + "verification of code. It is much more efficient to retrieve "
                + "resources by identifier (e.g. `R.foo.bar`) than by name (e.g. "
                + "`getIdentifier(\"bar\", \"foo\", null)`)."
            ),
    )
    class StringByNameResource(
        private val name: String,
        @StringRes private val fallbackId: Int,
        context: Context,
        private vararg val args: Any,
    ) : LbcTextSpec() {
        private val packageName = context.packageName

        override val annotated: AnnotatedString
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = AnnotatedString(string)

        override val string: String
            @Composable
            @ReadOnlyComposable
            @Suppress("SpreadOperator")
            get() = stringResource(getStringIdByName(LocalResources.current, name) ?: fallbackId, *args.resolveArgs())

        override fun string(resources: Resources): String {
            @Suppress("SpreadOperator")
            return resources
                .getString(getStringIdByName(resources, name) ?: fallbackId, *args.resolveArgsContext(resources))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            @SuppressLint("DiscouragedApi")
            other as StringByNameResource

            if (name != other.name) return false
            if (fallbackId != other.fallbackId) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + fallbackId
            result = 31 * result + args.contentHashCode()
            return result
        }

        @StringRes
        private fun getStringIdByName(resources: Resources, name: String): Int? = resources
            .getIdentifier(
                name,
                "string",
                packageName,
            ).takeIf { id -> id != 0 }
    }
}
