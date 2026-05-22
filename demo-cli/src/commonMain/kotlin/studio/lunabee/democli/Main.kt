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

package studio.lunabee.democli

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import studio.lunabee.core.model.LBFlowResult
import studio.lunabee.logger.LBLogger
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private const val Usage: String = "usage: demo-cli <seconds> [--verbose|-v]"
private const val BarWidth: Int = 20
private const val TickMs: Int = 100
private const val FilledBarChar: Char = '█'
private const val EmptyBarChar: Char = '░'
private const val ExitInvalidArgs: Int = 2
private const val ExitFailure: Int = 1

// ANSI: move cursor up one line, clear that line, return cursor to column 0.
private const val ClearLineUp: String = "[1A[2K\r"

fun main(args: Array<String>) {
    val total = parseTotal(args) ?: run {
        println(Usage)
        exitProcess(ExitInvalidArgs)
    }
    val verbose = args.drop(1).any { it == "--verbose" || it == "-v" }
    val logger: Logger? = if (verbose) {
        Logger.setLogWriters(CommonWriter())
        LBLogger.get("demo-cli")
    } else {
        null
    }
    logger?.i { "Starting chronometer total=${total.toDisplay()} tick=${TickMs}ms" }

    val exitCode = runBlocking { runChronometer(total = total, logger = logger) }
    exitProcess(exitCode)
}

private fun parseTotal(args: Array<String>): Duration? {
    val seconds = args.firstOrNull()?.toDoubleOrNull() ?: return null
    if (seconds <= 0.0) return null
    return seconds.seconds
}

private class RenderState {
    var hasPendingTickLine: Boolean = false
    var lastLoggedSecond: Long = -1L
}

private suspend fun runChronometer(total: Duration, logger: Logger?): Int {
    val state = RenderState()
    var failed = false
    chronometer(total = total, tick = TickMs.milliseconds).collect { result ->
        when (result) {
            is LBFlowResult.Loading -> handleLoading(result = result, total = total, logger = logger, state = state)

            is LBFlowResult.Success -> handleSuccess(result = result, total = total, logger = logger, state = state)

            is LBFlowResult.Failure -> {
                handleFailure(result = result, logger = logger, state = state)
                failed = true
            }
        }
    }
    return if (failed) ExitFailure else 0
}

private fun handleLoading(
    result: LBFlowResult.Loading<Duration>,
    total: Duration,
    logger: Logger?,
    state: RenderState,
) {
    val elapsed = result.partialData ?: Duration.ZERO
    val progress = result.progress ?: 0f
    val whole = elapsed.inWholeSeconds
    if (logger != null && whole > state.lastLoggedSecond) {
        if (state.hasPendingTickLine) print(ClearLineUp)
        logger.i { "tick elapsed=${elapsed.toDisplay()} progress=${(progress * 100).toInt()}%" }
        state.lastLoggedSecond = whole
        state.hasPendingTickLine = false
    }
    if (state.hasPendingTickLine) print(ClearLineUp)
    println(formatLine(elapsed = elapsed, total = total, progress = progress, done = false))
    state.hasPendingTickLine = true
}

private fun handleSuccess(
    result: LBFlowResult.Success<Duration>,
    total: Duration,
    logger: Logger?,
    state: RenderState,
) {
    if (state.hasPendingTickLine) print(ClearLineUp)
    println(formatLine(elapsed = result.successData, total = total, progress = 1f, done = true))
    state.hasPendingTickLine = false
    logger?.i { "Chronometer completed: ${result.successData.toDisplay()}" }
}

private fun handleFailure(result: LBFlowResult.Failure<Duration>, logger: Logger?, state: RenderState) {
    if (state.hasPendingTickLine) print(ClearLineUp)
    println("error: ${result.throwable?.message ?: "unknown failure"}")
    logger?.e("Chronometer failed", result.throwable ?: Exception("unknown failure"))
    state.hasPendingTickLine = false
}

private fun formatLine(elapsed: Duration, total: Duration, progress: Float, done: Boolean): String {
    val clamped = progress.coerceIn(0f, 1f)
    val filled = (clamped * BarWidth).toInt()
    val empty = BarWidth - filled
    val bar = buildString {
        append('[')
        repeat(filled) { append(FilledBarChar) }
        repeat(empty) { append(EmptyBarChar) }
        append(']')
    }
    val pct = (clamped * 100).toInt()
    val suffix = if (done) " ✓ done" else ""
    return "$bar ${elapsed.toDisplay()} / ${total.toDisplay()} ($pct%)$suffix"
}

private fun Duration.toDisplay(): String = toString(unit = DurationUnit.SECONDS, decimals = 3)
