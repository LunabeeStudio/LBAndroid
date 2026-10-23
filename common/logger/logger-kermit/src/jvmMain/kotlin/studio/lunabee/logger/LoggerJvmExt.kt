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

package studio.lunabee.logger

import co.touchlab.kermit.Logger
import org.jetbrains.annotations.NonNls

/*
 * Timber like extensions
 *
 * The `(t, message, args)` overloads must pass a non-null message to Kermit: a `String?` resolves to the
 * `(message, arg)` extension instead, which formats the message with `t` as argument and throws on any `%` it contains.
 */

/** Log a verbose message with one format arg. */
fun Logger.v(@NonNls message: String?, arg: Any?) {
    val formattedMessage = formatMessage(arrayOf(arg), message).orEmpty()
    this.v(formattedMessage)
}

/** Log a verbose message with optional format args. */
fun Logger.v(@NonNls message: String?, vararg args: Any?) {
    val formattedMessage = formatMessage(args, message).orEmpty()
    this.v(formattedMessage)
}

/** Log a verbose exception and a message with optional format args. */
fun Logger.v(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
    this.v(formatMessage(args, message).orEmpty(), t)
}

/** Log a verbose exception. */
fun Logger.v(t: Throwable) {
    this.v("", t)
}

/** Log a debug message with one format arg. */
fun Logger.d(@NonNls message: String?, arg: Any?) {
    val formattedMessage = formatMessage(arrayOf(arg), message).orEmpty()
    this.d(formattedMessage)
}

/** Log a debug message with optional format args. */
fun Logger.d(@NonNls message: String?, vararg args: Any?) {
    val formattedMessage = formatMessage(args, message).orEmpty()
    this.d(formattedMessage)
}

/** Log a debug exception and a message with optional format args. */
fun Logger.d(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
    this.d(formatMessage(args, message).orEmpty(), t)
}

/** Log a debug exception. */
fun Logger.d(t: Throwable) {
    this.d("", t)
}

/** Log a info message with one format arg. */
fun Logger.i(@NonNls message: String?, arg: Any?) {
    val formattedMessage = formatMessage(arrayOf(arg), message).orEmpty()
    this.i(formattedMessage)
}

/** Log an info message with optional format args. */
fun Logger.i(@NonNls message: String?, vararg args: Any?) {
    val formattedMessage = formatMessage(args, message).orEmpty()
    this.i(formattedMessage)
}

/** Log an info exception and a message with optional format args. */
fun Logger.i(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
    this.i(formatMessage(args, message).orEmpty(), t)
}

/** Log an info exception. */
fun Logger.i(t: Throwable) {
    this.i("", t)
}

/** Log a warning message with one format arg. */
fun Logger.w(@NonNls message: String?, arg: Any?) {
    val formattedMessage = formatMessage(arrayOf(arg), message).orEmpty()
    this.w(formattedMessage)
}

/** Log a warning message with optional format args. */
fun Logger.w(@NonNls message: String?, vararg args: Any?) {
    val formattedMessage = formatMessage(args, message).orEmpty()
    this.w(formattedMessage)
}

/** Log a warning exception and a message with optional format args. */
fun Logger.w(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
    this.w(formatMessage(args, message).orEmpty(), t)
}

/** Log a warning exception. */
fun Logger.w(t: Throwable) {
    this.w("", t)
}

/** Log a error message with one format arg. */
fun Logger.e(@NonNls message: String?, arg: Any?) {
    val formattedMessage = formatMessage(arrayOf(arg), message).orEmpty()
    this.e(formattedMessage)
}

/** Log an error message with optional format args. */
fun Logger.e(@NonNls message: String?, vararg args: Any?) {
    val formattedMessage = formatMessage(args, message).orEmpty()
    this.e(formattedMessage)
}

/** Log an error exception and a message with optional format args. */
fun Logger.e(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
    this.e(formatMessage(args, message).orEmpty(), t)
}

@Suppress("SpreadOperator")
private fun formatMessage(args: Array<out Any?>, @NonNls message: String?): String? {
    val formattedMessage = if (args.isNotEmpty()) {
        message?.format(*args)
    } else {
        message
    }
    return formattedMessage
}
