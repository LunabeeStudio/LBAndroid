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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Only test no crash on log extension call
 */
class LoggerExtTest {
    val logger: Logger = LBLogger.get("testLogger")

    @Test
    fun verbose_extensions_test() {
        logger.v(message = "%d", arg = 1)
        logger.v(message = "%d %s", 1, "hello")
        logger.v(Exception("Error text"), message = "%d %s", 1, "hello")
        logger.v(Exception("Error text"))
    }

    @Test
    fun debug_extensions_test() {
        logger.d(message = "%d", arg = 1)
        logger.d(message = "%d %s", 1, "hello")
        logger.d(Exception("Error text"), message = "%d %s", 1, "hello")
        logger.d(Exception("Error text"))
    }

    @Test
    fun info_extensions_test() {
        logger.i(message = "%d", arg = 1)
        logger.i(message = "%d %s", 1, "hello")
        logger.i(Exception("Error text"), message = "%d %s", 1, "hello")
        logger.i(Exception("Error text"))
    }

    @Test
    fun warn_extensions_test() {
        logger.w(message = "%d", arg = 1)
        logger.w(message = "%d %s", 1, "hello")
        logger.w(Exception("Error text"), message = "%d %s", 1, "hello")
        logger.w(Exception("Error text"))
    }

    @Test
    fun err_extensions_test() {
        logger.e(message = "%d", arg = 1)
        logger.e(message = "%d %s", 1, "hello")
        logger.e(Exception("Error text"), message = "%d %s", 1, "hello")
        logger.e(Exception("Error text"))
    }

    @Test
    fun throwable_message_is_not_formatted_test() {
        val writer = RecordingLogWriter()
        val recordingLogger = Logger(config = loggerConfigInit(writer), tag = "testLogger")
        val error = Exception("Error text")

        recordingLogger.v(error, message = "100% done")
        recordingLogger.d(error, message = "100% done")
        recordingLogger.i(error, message = "100% done")
        recordingLogger.w(error, message = "100% done")
        recordingLogger.e(error, message = "100% done")

        val expected = Severity.entries.filter { it != Severity.Assert }.map { severity -> Triple(severity, "100% done", error) }
        assertEquals(expected, writer.logs)
    }
}

private class RecordingLogWriter : LogWriter() {
    val logs: MutableList<Triple<Severity, String, Throwable?>> = mutableListOf()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        logs += Triple(severity, message, throwable)
    }
}
